package io.nop.rpc.client;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

public interface EchoService {
    @Path("/echo/{id}")
    String echo(@QueryParam("msg") String msg, @PathParam("id") String id);
}