package io.nop.http.api.server;

public interface IClientIpFetcher {
    String getClientRealIp(IHttpServerContext context);

    String getClientRealAddr(IHttpServerContext context);
}
