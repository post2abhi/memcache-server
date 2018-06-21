/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server;

import com.github.ak.memcache.server.codec.EncoderDecoder;
import com.github.ak.memcache.server.codec.GetCommandRequest;
import com.github.ak.memcache.server.codec.MemcacheRequest;
import com.github.ak.memcache.server.codec.QuitCommandRequest;
import com.github.ak.memcache.server.codec.SetCommandRequest;
import io.netty.channel.ChannelHandlerContext;

/**
 * Handles <em>memcache get, set, quit</em> commands.
 * <p/>
 * When {@code set} is received, state machine transitions to
 * {@link AcceptDataState}.
 * <p/>
 * When {@code quit} is received, client's connection
 * {@link java.nio.channels.SocketChannel} is terminated.
 *
 */
public class AcceptCommandState implements HandlerState {
    @Override
    public void handle(MemcacheProtocolHandler protocolHandler,
                       ChannelHandlerContext channelHandler,
                       MemcacheRequest request)
            throws MemcacheProtocolException{

        if (request instanceof SetCommandRequest){
            handleSet(protocolHandler, (SetCommandRequest)request);

        }else if (request instanceof GetCommandRequest){
            handleGet(protocolHandler, channelHandler, (GetCommandRequest)request);

        }else if (request instanceof QuitCommandRequest){
            handleQuit(channelHandler);
        }else{
            throw new MemcacheProtocolException.InvalidCommandException();
        }
    }

    private void handleGet(MemcacheProtocolHandler protocolHandler,
                           ChannelHandlerContext channelHandler,
                           GetCommandRequest request){

        StringBuilder response = new StringBuilder();
        for (String key: (request).getKeys()){
            String data = protocolHandler.getCache().get(key);
            if (data != null) {
                response.append(EncoderDecoder.encodeData(key, data));
            }
        }
        response.append(EncoderDecoder.endMarker());
        channelHandler.write(response.toString());
    }

    private void handleSet(MemcacheProtocolHandler protocolHandler, SetCommandRequest request){
        protocolHandler.setState(new AcceptDataState(request));
    }

    private void handleQuit(ChannelHandlerContext channelHandler){
        channelHandler.close();
    }
}
