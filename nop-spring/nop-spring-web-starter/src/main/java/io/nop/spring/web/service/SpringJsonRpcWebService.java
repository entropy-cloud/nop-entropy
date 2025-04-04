package io.nop.spring.web.service;

import io.nop.graphql.core.web.GraphQLWebService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.CompletionStage;

@RestController
@ConditionalOnProperty(name = "nop.graphql.json-rpc.enabled", matchIfMissing = true)
public class SpringJsonRpcWebService extends GraphQLWebService {


    @PostMapping("/jsonrpc")
    public CompletionStage<ResponseEntity<Object>> jsonRpcSpring(@RequestBody(required = false) String body) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null)
            throw new IllegalStateException("null request context");

        return runJsonRpc(body, getHeaders()).thenApply(ret -> {
            ret.setHeader(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
            return SpringMvcHelper.buildResponseEntity(ret.getHeaders(), ret.getData(), ret.getHttpStatus());
        });
    }
}