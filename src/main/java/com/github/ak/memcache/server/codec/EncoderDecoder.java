/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server.codec;

import com.github.ak.memcache.server.MemcacheProtocolException;

import java.util.Optional;

public interface EncoderDecoder {

    String LINE_SEPARATER = "\r\n";
    String ERROR_RESPONSE = "%s %s" + LINE_SEPARATER;
    //2nd param 0 is flag that we have not implemented, so just hardcoded
    String DATA_RESPONSE = "VALUE %s 0 %s" + LINE_SEPARATER + "%s" + LINE_SEPARATER;
    String END_MARKER = "END" + LINE_SEPARATER;
    String STORED_RESPONSE = "STORED" + LINE_SEPARATER;

    static MemcacheRequest decode(String request) throws MemcacheProtocolException {

        if (request.startsWith(MemcacheRequest.Command.GET.toString())){
            return new GetCommandRequest(request);
        }else if (request.startsWith(MemcacheRequest.Command.SET.toString())){
            return new SetCommandRequest(request);
        }else if (request.startsWith(MemcacheRequest.Command.QUIT.toString())){
            return new QuitCommandRequest(request);
        }else {
            return new DataRequest(request);
        }
    }

    static String encodeException(MemcacheProtocolException e){
        return String.format(ERROR_RESPONSE, e.getCode(),
                Optional.ofNullable(e.getMessage()).orElse(""));
    }

    static String encodeData(String key, String value){
        return String.format(DATA_RESPONSE, key, value.length(), value);
    }

    static String storedResponse(){
        return STORED_RESPONSE;
    }

    static String endMarker(){
        return END_MARKER;
    }
}
