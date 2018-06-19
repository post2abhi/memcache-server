/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server;

import com.github.ak.memcache.server.codec.MemcacheRequest;
import io.netty.channel.ChannelHandlerContext;

/**
 * State in which {@link MemcacheProtocolHandler} state machine be in.
 */
public interface HandlerState {
    void handle(MemcacheProtocolHandler protocolHandler,
                ChannelHandlerContext channelHandler,
                MemcacheRequest request)
            throws MemcacheProtocolException;
}
