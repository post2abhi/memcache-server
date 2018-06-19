/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server;

import com.github.ak.memcache.cache.Cache;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

/**
 * This code is largely based on <em>netty examples</em> with some customizations.
 *
 * @see <a href="https://github.com/netty/netty/tree/4.0/example/src/main/java/io/netty/example">netty examples</a>
 */
public class Initializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_FRAME_LENGTH = 8192;
    private static final StringDecoder decoder = new StringDecoder();
    private static final StringEncoder encoder = new StringEncoder();

    private final Cache<String,String> cache;

    public Initializer(Cache<String,String> cache){
        this.cache = cache;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // Add the text line codec combination first,
        pipeline.addLast(new DelimiterBasedFrameDecoder(MAX_FRAME_LENGTH, Delimiters.lineDelimiter()));
        // the encoder and decoder are static as these are sharable
        pipeline.addLast(decoder);
        pipeline.addLast(encoder);

        //Protocol handler is instantiated per channel
        pipeline.addLast(new MemcacheProtocolHandler(cache));
    }
}
