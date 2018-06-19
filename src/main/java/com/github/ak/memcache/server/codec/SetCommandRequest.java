/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server.codec;

import com.google.common.base.Preconditions;

public class SetCommandRequest implements MemcacheRequest {
    private String message;
    private String key;//token#2
    private int flags;//token#3
    private long expTime;//token#4
    private int numDataBytes;//token#5
    private boolean noReply=false; //token#6;optional

    private static final int EXPECTED_TOKENS = 5;//atleast

    public SetCommandRequest(String request){
        Preconditions.checkNotNull(request);
        this.message = request;
        parse();
    }

    @Override
    public String getRawMessage(){
        return this.message;
    }
    public String getKey() {
        return key;
    }

    public int getFlags() {
        return flags;
    }

    public long getExpTime() {
        return expTime;
    }

    public int getNumDataBytes() {
        return numDataBytes;
    }

    public boolean isReplyMuted(){
        return noReply;
    }

    /**
     * <command name> <key> <flags> <exptime> <bytes> [noreply]\r\n
     */
    private void parse(){
        String[] tokens = message.split("\\s+");
        Preconditions.checkState(tokens.length >= EXPECTED_TOKENS);

        key = tokens[1];
        flags = Integer.parseInt(tokens[2]);
        expTime = Long.parseLong(tokens[3]);
        numDataBytes = Integer.parseInt(tokens[4]);

        if (tokens.length == 6){
            noReply = Boolean.parseBoolean(tokens[5]);
        }
    }
}
