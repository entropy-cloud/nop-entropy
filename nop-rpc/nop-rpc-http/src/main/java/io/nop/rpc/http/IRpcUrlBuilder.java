package io.nop.rpc.http;

import io.nop.api.core.beans.ApiRequest;

public interface IRpcUrlBuilder {
    String buildUrl(ApiRequest<?> req, String serviceMethod);

    default String toHttpHeader(String name) {
        return name;
    }
}
