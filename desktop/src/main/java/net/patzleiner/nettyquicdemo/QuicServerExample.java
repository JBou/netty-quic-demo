/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package net.patzleiner.nettyquicdemo;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public final class QuicServerExample {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicServerExample.class);
    private static int PORT = 9999;

    private QuicServerExample() { }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting");
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        QuicSslContext context = QuicSslContextBuilder.forServer(
                selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
                .applicationProtocols("http/0.9").build();
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ChannelHandler codec = new QuicServerCodecBuilder().sslContext(context)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                // Configure some limits for the maximal number of streams (and the data) that we wiwant to handle.
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)

                // Setup a token handler. In a production system you would want to implement and provide your custom
                // one.
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                // ChannelHandler that is added into QuicChannel pipeline.
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        QuicChannel channel = (QuicChannel) ctx.channel();
                        // Create streams etc..
                    }

                    public void channelInactive(ChannelHandlerContext ctx) {
                        ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
                            if (f.isSuccess()) {
                                LOGGER.info("Connection closed: {}", f.getNow());
                            }
                        });
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                })
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch)  {
                        // Add a LineBasedFrameDecoder here as we just want to do some simple HTTP 0.9 handling.
                        ch.pipeline().addLast(new LineBasedFrameDecoder(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                ByteBuf byteBuf = (ByteBuf) msg;
                                try {
                                    LOGGER.info(byteBuf.toString(CharsetUtil.US_ASCII));
                                    ByteBuf buffer = ctx.alloc().directBuffer();

                                    buffer.writeCharSequence("Pong! at " + new Date().toString() + "\r\n", CharsetUtil.US_ASCII);
                                    //ByteBuf buffer = Unpooled.copiedBuffer("Pong! at " + new Date().toString() + "\r\n", CharsetUtil.US_ASCII);

                                    // Write the buffer and shutdown the output by writing a FIN.
                                    ctx.writeAndFlush(buffer);//.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                } finally {
                                    byteBuf.release();
                                }
                            }
                        });
                    }
                }).build();
        try {
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(new InetSocketAddress(PORT)).sync().channel();
            System.out.println("Started on port " + PORT);
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
