package com.github.ak.memcache.server;

import com.github.ak.memcache.cache.SimpleCache;
import com.github.ak.memcache.server.codec.EncoderDecoder;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.github.ak.memcache.server.codec.EncoderDecoder.STORED_RESPONSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemcacheProtocolHandlerTest {

    ChannelHandlerContext mockChannel;
    MemcacheProtocolHandler handler;

    @BeforeEach
    void setup(){
        mockChannel = mock(ChannelHandlerContext.class, Mockito.RETURNS_SMART_NULLS);
        handler = new MemcacheProtocolHandler(new SimpleCache());
    }

    /** State machine tests **/
    @Test
    void whenInitialized_shouldAcceptCommand(){
        assertTrue(handler.getState() instanceof AcceptCommandState);
    }

    @Test
    void afterGetCommand_shouldAcceptCommand(){
        final String aGetCommand = "get some-key";
        handler.channelRead0(mockChannel, aGetCommand);
        assertTrue(handler.getState() instanceof AcceptCommandState);
    }

    @Test
    void afterSetCommand_shouldAcceptData(){
        final String aSetCommand = "set some-key 0 0 5";
        handler.channelRead0(mockChannel, aSetCommand);
        assertTrue(handler.getState() instanceof AcceptDataState);
    }

    /** Memcache protocol tests **/
    @Test
    void givenSetCommand_whenValid_shouldStore(){
        final String aSetCommand = "set some-key 0 0 5";
        final String data = "abcde";

        handler.channelRead0(mockChannel, aSetCommand);
        assertTrue(handler.getState() instanceof AcceptDataState);
        handler.channelRead0(mockChannel, data);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(mockChannel).write(arg.capture());
        assertEquals(STORED_RESPONSE, arg.getValue());
        assertTrue(handler.getState() instanceof AcceptCommandState);
    }

    @Test
    void givenSetCommand_whenInValid_shouldError(){
        final String aSetCommand = "set some-key 0 0 5";
        final String data = "abcdef"; //invalid length 6>5

        handler.channelRead0(mockChannel, aSetCommand);
        assertTrue(handler.getState() instanceof AcceptDataState);
        handler.channelRead0(mockChannel, data);

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(mockChannel).write(arg.capture());
        final String expectedResponse = EncoderDecoder.encodeException(
                new MemcacheProtocolException.ClientException("Data size exceeded"));
        assertEquals(expectedResponse, arg.getValue());
        assertTrue(handler.getState() instanceof AcceptCommandState);
    }

    @Test
    void givenGetCommand_whenKeyExists_shouldReturn(){
        final String key = "some-key";
        final String data = "abcde";
        final String aSetCommand = "set " + key + " 0 0 5";
        final String aGetCommand = "get " + key;

        handler.channelRead0(mockChannel, aSetCommand);
        handler.channelRead0(mockChannel, data);

        handler.channelRead0(mockChannel, aGetCommand);
        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(mockChannel, atMost(2)).write(arg.capture());
        final String expectedResponse = EncoderDecoder.encodeData(key, data) + EncoderDecoder.endMarker();
        assertEquals(expectedResponse, arg.getValue());
        assertTrue(handler.getState() instanceof AcceptCommandState);
    }
}