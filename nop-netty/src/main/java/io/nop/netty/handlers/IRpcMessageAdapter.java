package io.nop.netty.handlers;

public interface IRpcMessageAdapter {
    Object getRequestId(Object request);

    Object getResponseId(Object response);
}
