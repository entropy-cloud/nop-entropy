package spring.test;

import io.nop.api.core.beans.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

@RestController
public class EchoService {
    @PostMapping(value = "/echo/{id}")
    public String echo(@QueryParam("msg") String msg, @PathParam("id") String id) {
        return "Hello Nacos Discovery " + msg + ",id=" + id;
    }
}