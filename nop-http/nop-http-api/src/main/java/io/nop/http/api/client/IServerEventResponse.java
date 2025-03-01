package io.nop.http.api.client;

import io.nop.http.api.IHttpHeaders;

public interface IServerEventResponse extends IHttpHeaders {
    int getHttpStatus();

    String getEvent();

    String getData();
}
