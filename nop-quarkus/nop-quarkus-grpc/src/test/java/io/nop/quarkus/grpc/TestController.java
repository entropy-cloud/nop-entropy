package io.nop.quarkus.grpc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@ApplicationScoped
@Path("")
public class TestController {
    //增加一个REST服务方法
    @Path("/hello")
    @GET
    public String hello() {
        return "Hello from Quarkus!";
    }
}
