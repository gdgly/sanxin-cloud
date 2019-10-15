package com.sanxin.cloud.netty.server;

import com.sanxin.cloud.netty.handle.AppDataAcceptInitializer;
import com.sanxin.cloud.netty.handle.DataAcceptInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

/**
 * App、机柜中间通信Server
 * @author xiaoky
 * @date 2019-10-11
 */
@RequiredArgsConstructor
@Component
public class AppNettyServer {
    private static Logger log = LoggerFactory.getLogger(NettyServer.class);

    @Value("${netty.appPort}")
    private Integer port;
    @Autowired
    private AppDataAcceptInitializer appDataAcceptInitializer;

    private static final int BIZGROUPSIZE = Runtime.getRuntime().availableProcessors() * 2;

    private static final int BIZTHREADSIZE = 100;
    private static final EventLoopGroup bossGroup = new NioEventLoopGroup(BIZGROUPSIZE);
    private static final EventLoopGroup workerGroup = new NioEventLoopGroup(BIZTHREADSIZE);

    public void init(){
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        //保持长连接
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.childHandler(appDataAcceptInitializer);
        Thread thread = new Thread() {
            public void run() {
                ChannelFuture f = null;
                try {
                    f = bootstrap.bind(port).sync();
                    f.channel().closeFuture().sync();
                } catch (InterruptedException e) {
                    log.info("TCP服务器启动失败"+e.getMessage());
                }

            }
        };
        thread.start();
        log.info("TCP服务器已启动");
    }


    /**
     * 销毁
     */
    @PreDestroy
    public void destroy() {
        bossGroup.shutdownGracefully().syncUninterruptibly();
        workerGroup.shutdownGracefully().syncUninterruptibly();
        log.info("关闭 Netty 成功");
    }
}
