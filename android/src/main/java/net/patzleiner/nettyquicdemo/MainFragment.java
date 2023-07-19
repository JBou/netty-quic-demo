package net.patzleiner.nettyquicdemo;

import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class MainFragment extends Fragment {

    private TextView mTextView;
    private Button mButton;
    private String inetAddress = "192.168.1.6";
    private int port = 9999;

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_fragment, container, false);
        mTextView = view.findViewById(R.id.message);
        mButton = view.findViewById(R.id.button);
        mTextView.setMovementMethod(new ScrollingMovementMethod());
        mButton.setOnClickListener(v -> OnButtonClick());
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // TODO: Use the ViewModel
    }

    private void OnButtonClick() {

        Log.d("SUPPORTED_ABIS", String.join(", ", Build.SUPPORTED_ABIS));

        Thread thread = new Thread(() -> {
            QuicSslContext context = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).
                    applicationProtocols("http/0.9").build();
            NioEventLoopGroup group = new NioEventLoopGroup(1);
            try {
                ChannelHandler codec = new QuicClientCodecBuilder()
                        .sslContext(context)
                        .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                        .initialMaxData(10000000)
                        // As we don't want to support remote initiated streams just setup the limit for local initiated
                        // streams in this example.
                        .initialMaxStreamDataBidirectionalLocal(1000000)
                        .build();

                Bootstrap bs = new Bootstrap();
                Channel channel = bs.group(group)
                        .channel(NioDatagramChannel.class)
                        .handler(codec)
                        .bind(0).sync().channel();

                QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                        .streamHandler(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(@NotNull ChannelHandlerContext ctx) {
                                // As we did not allow any remote initiated streams we will never see this method called.
                                // That said just let us keep it here to demonstrate that this handle would be called
                                // for each remote initiated stream.
                                ctx.close();
                            }
                        })
                        .remoteAddress(new InetSocketAddress(inetAddress, port))
                        //.remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST4, 9999))
                        .connect()
                        .get();

                requireActivity().runOnUiThread(() -> addMessage(new Date() + " -> Connected \r\n"));

                QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) {
                                ByteBuf byteBuf = (ByteBuf) msg;
                                requireActivity().runOnUiThread(
                                        () -> addMessage(byteBuf.toString(CharsetUtil.US_ASCII) + "\r\n"));
                                byteBuf.release();
                            }

                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                    // Close the connection once the remote peer did send the FIN for this stream.
                                    ((QuicChannel) ctx.channel().parent()).close(true, 0,
                                            ctx.alloc().directBuffer(16)
                                                    .writeBytes(new byte[]{'k', 't', 'h', 'x', 'b', 'y', 'e'}));
                                    requireActivity().runOnUiThread(
                                            () -> addMessage(new Date() + " -> Disconnected \r\n"));
                                }
                            }
                        }).sync().getNow();

                //Let's send a message every second
                group.scheduleAtFixedRate(() ->
                                streamChannel.writeAndFlush(Unpooled.copiedBuffer("Ping at " + new Date() + "\r\n", CharsetUtil.US_ASCII)),
                        0, 1, TimeUnit.SECONDS);


                // Write the data and send the FIN. After this its not possible anymore to write any more data.
                //streamChannel.writeAndFlush(Unpooled.copiedBuffer("GET /\r\n", CharsetUtil.US_ASCII))
                //       .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);


                // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
                // After this is done we will close the underlying datagram channel.
                streamChannel.closeFuture().sync();
                quicChannel.closeFuture().sync();
                channel.close().sync();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            } finally {
                group.shutdownGracefully();
            }
        });
        thread.start();
    }

    // function to append a string to a TextView as a new line
    // and scroll to the bottom if needed
    private void addMessage(String msg) {
        // append the new string
        mTextView.append(msg + "\n");
        // find the amount we need to scroll.  This works by
        // asking the TextView's internal layout for the position
        // of the final line and then subtracting the TextView's height
        final int scrollAmount = mTextView.getLayout().getLineTop(mTextView.getLineCount()) - mTextView.getHeight();
        // if there is no need to scroll, scrollAmount will be <=0
        mTextView.scrollTo(0, Math.max(scrollAmount, 0));
    }


}
