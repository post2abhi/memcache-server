/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server;

import com.github.ak.memcache.server.codec.MemcacheRequest;
import com.github.ak.memcache.server.codec.SetCommandRequest;
import io.netty.channel.ChannelHandlerContext;

/**
 * State machine accepts data in this state. The state machine
 * goes into this state after serving a <em>memcache data storage</em>
 * command eg: <em>set</em>.
 * <p/>
 * State machine passes the previous command {@link AcceptDataState#command}
 * into this state.
 * <p/>
 * In this state, the state machine keeps buffering{@link AcceptDataState#buffer}
 * data sent by client until all bits are received.
 *
 */
public class AcceptDataState implements HandlerState {

    private SetCommandRequest command;
    private StringBuilder buffer;

    public AcceptDataState(SetCommandRequest command){
        this.command = command;
        buffer = new StringBuilder();
    }

    @Override
    public void handle(MemcacheProtocolHandler protocolHandler,
                       ChannelHandlerContext channelHandler,
                       MemcacheRequest request) throws MemcacheProtocolException {

        buffer.append(request.getRawMessage());
        if (buffer.length() <= command.getNumDataBytes()-1){
            buffer.append("\n");
        }
        if (buffer.length() == command.getNumDataBytes()) {
            protocolHandler.getCache().put(command.getKey(), buffer.toString());
            protocolHandler.setState(new AcceptCommandState());
            channelHandler.write("STORED\r\n");
        }else if (buffer.length() > command.getNumDataBytes()){
            throw new MemcacheProtocolException.ClientException("Data size exceeded");
        }

    }
}
