package spring.test;

import io.nop.api.core.beans.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EchoService {
    @PostMapping(value = "/r/EchoService__echo")
    public ApiResponse echo(@RequestBody String string) {
        return ApiResponse.buildSuccess("Hello Nacos Discovery " + string);
    }
}