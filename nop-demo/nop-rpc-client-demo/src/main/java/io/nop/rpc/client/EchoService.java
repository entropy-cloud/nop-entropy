package io.nop.rpc.client;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

public interface EchoService {
    @Path("/echo/{id}")
    String echo(@QueryParam("msg") String msg, @PathParam("id") String id);
}