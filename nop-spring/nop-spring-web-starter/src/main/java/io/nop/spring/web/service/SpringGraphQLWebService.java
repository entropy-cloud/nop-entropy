/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.web.service;

import io.nop.commons.util.CollectionHelper;
import io.nop.graphql.core.web.GraphQLWebService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_ARGS;
import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_SELECTION;

@Path("")
@RestController
public class SpringGraphQLWebService extends GraphQLWebService {

    @Override
    protected Map<String, String> getParams() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs.getRequest();
        Map<String, String> ret = new HashMap<>();
        for (String paramName : request.getParameterMap().keySet()) {
            ret.put(paramName, request.getParameter(paramName));
        }
        return ret;
    }

    @POST
    @Path("/graphql")
    @Produces(MediaType.APPLICATION_JSON)
    @PostMapping(path = "/graphql", produces = MediaType.APPLICATION_JSON)
    public CompletionStage<ResponseEntity<Object>> graphqlSpring(@RequestBody String body) {
        return super.graphql(body).thenApply(this::transformResponse);
    }

    protected ResponseEntity<Object> transformResponse(Response response) {
        HttpHeaders headers = new HttpHeaders();
        response.getHeaders().forEach((name, value) -> {
            List<String> list = CollectionHelper.toStringList(value);
            headers.put(name, list);
        });
        Object body = response.getEntity();
        ResponseEntity<Object> res = new ResponseEntity<>(body, headers, HttpStatus.valueOf(response.getStatus()));
        return res;
    }

    @POST
    @Path("/r/{operationName}")
    @Produces(MediaType.APPLICATION_JSON)
    @PostMapping(path = "/r/{operationName}", produces = MediaType.APPLICATION_JSON)
    public CompletionStage<ResponseEntity<Object>> restSpring(@PathVariable("operationName") String operationName,
                                                              @RequestParam(SYS_PARAM_SELECTION) String selection,
                                                              @RequestBody String body) {
        return super.rest(operationName, selection, body).thenApply(this::transformResponse);
    }

    @GET
    @Path("/r/{operationName}")
    @Produces(MediaType.APPLICATION_JSON)
    @GetMapping(path = "/r/{operationName}", produces = MediaType.APPLICATION_JSON)
    public CompletionStage<ResponseEntity<Object>> restQuerySpring(@PathVariable("operationName") String operationName,
                                                                   @RequestParam(SYS_PARAM_SELECTION) String selection,
                                                                   @RequestParam(SYS_PARAM_ARGS) String args) {
        return super.restQuery(operationName, selection, args).thenApply(this::transformResponse);
    }
}