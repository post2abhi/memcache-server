/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server;

import com.github.ak.memcache.cache.Cache;
import com.github.ak.memcache.server.codec.MemcacheRequestDecoder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * This handles <em>memcache protocol</em> messages. Its not thread-safe,
 * so must be instantiated for every new client connection
 * ({@link java.nio.channels.SocketChannel}).
 * <p/>
 * This works as a state machine and can be in the following 2 states:
 * <ul>
 * <li>{@link AcceptCommandState} - accepts "get" or "set" memcache commands</li>
 * <li>{@link AcceptDataState} - accepts data corresponding to previous "set" command</li>
 * </ul>
 * <p/>
 * The state machine starts in {@link AcceptCommandState}. It transitions to
 * {@link AcceptDataState} when a <em>memcache "set"</em> command arrives and waits
 * for associated data to arrive. Once data arrives, it goes back to accepting commands.
 * <p/>
 * <em>memcache "get"</em> does not transition the state.
 *
 * @see <a href="https://github.com/memcached/memcached/blob/master/doc/protocol.txt">memcache protocol</a>
 * @see <a href="https://github.com/netty/netty/tree/4.0/example/src/main/java/io/netty/example">netty examples</a>
 *
 */
@NotThreadSafe
public class MemcacheProtocolHandler extends SimpleChannelInboundHandler<String> {

    static final Logger logger = LoggerFactory.getLogger(MemcacheProtocolHandler.class);

    private HandlerState state;
    private final Cache<String,String> cache;

    public MemcacheProtocolHandler(Cache<String,String> cache){
        this.cache = cache;
        state = new AcceptCommandState();
    }

    /**
     * When message is read on {@link java.nio.channels.SocketChannel}, this method
     * passes it to appropriate state handler after decoding the message.
     * <p/>
     * {@code null} or {@code empty} message is ignored.
     * <p/>
     * Any exception is translated to <em>memcache protocol errors</em>
     *
     * @param ctx   channel context object provided by netty framework
     * @param msg   message read on the channel
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, @Nullable String msg){

        if (msg!=null || !msg.isEmpty()){
            try {
                state.handle(this, ctx, MemcacheRequestDecoder.decode(msg));
            } catch (MemcacheProtocolException e) {
                ctx.write(e.toString()+ "\r\n");
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Unexpected error.", cause);
        ctx.close();
    }

    protected Cache<String, String> getCache(){
        return cache;
    }

    protected void setState(HandlerState state){
        this.state = state;
    }
}
