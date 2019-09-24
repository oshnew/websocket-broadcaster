package pbouda.broadcaster.server.netty;

import com.typesafe.config.Config;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public class Broadcaster implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Broadcaster.class);

    private final ServerBootstrap bootstrap;
    private final ChannelGroup channelGroup;
    private final EventLoopGroup bossEventLoopGroup;
    private final EventLoopGroup workerEventLoopGroup;
    private final Config config;

    public Broadcaster(Config config) {
        this.config = config;
        this.channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
        this.bossEventLoopGroup = new EpollEventLoopGroup(1);
        this.workerEventLoopGroup = new EpollEventLoopGroup();

        this.bootstrap = new ServerBootstrap()
                .channel(EpollServerSocketChannel.class)
                .group(bossEventLoopGroup, workerEventLoopGroup)
                .localAddress(config.getInt("port"))
                // .handler(new LoggingHandler(LogLevel.INFO))
                // .childOption(ChannelOption.SO_SNDBUF, 1024 * 1024)
                // .childOption(ChannelOption.SO_RCVBUF, 32 * 1024)
                .childHandler(new RouterChannelInitializer(config.getString("path"), channelGroup));
        // Let's fill up SO_SNDBUF to simulate a slow consumer
        // .childOption(ChannelOption.SO_SNDBUF, 4092);

        /*
         * The maximum queue length for incoming connection indications
         * (a request to connect) is set to the backlog parameter. If
         * a connection indication arrives when the queue is full,
         * the connection is refused.
         */
        // bootstrap.option(ChannelOption.SO_BACKLOG, 100);
        // bootstrap.handler(new LoggingHandler(LogLevel.INFO));

        // var certificate = new SelfSignedCertificate();
        // var sslContext = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey())
        //         .build();

        // Receive and Send Buffer - always be able to fill in an entire entity.
        // bootstrap.childOption(ChannelOption.SO_SNDBUF, 32 * 1024);
        // bootstrap.childOption(ChannelOption.SO_RCVBUF, 32 * 1024);
        // bootstrap.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark.DEFAULT);
    }

    public Channel start(GenericFutureListener<? extends Future<? super Void>> listener) {
        ChannelFuture serverBindFuture = bootstrap.bind();
        // Start consuming from Rabbit after the websocket server is started
        serverBindFuture.addListener(listener);
        // Wait for the binding is completed
        serverBindFuture.syncUninterruptibly();
        LOG.info("PID {} - Broadcaster started on port '{}' and path '{}'",
                ManagementFactory.getRuntimeMXBean().getPid(), config.getInt("port"), config.getString("path"));
        return serverBindFuture.channel();
    }

    public ChannelGroup getChannelGroup() {
        return channelGroup;
    }

    @Override
    public void close() {
        Future<?> boss = bossEventLoopGroup.shutdownGracefully();
        Future<?> workers = workerEventLoopGroup.shutdownGracefully();
        boss.syncUninterruptibly();
        workers.syncUninterruptibly();
    }
}