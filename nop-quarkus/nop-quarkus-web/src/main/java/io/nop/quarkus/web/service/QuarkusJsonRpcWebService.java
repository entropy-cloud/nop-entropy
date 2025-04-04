package io.nop.quarkus.web.service;

import io.nop.graphql.core.web.GraphQLWebService;
import io.nop.graphql.core.web.JaxrsHelper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.concurrent.CompletionStage;

import static io.nop.quarkus.web.utils.QuarkusExecutorHelper.withRoutingContext;

@Path("")
@ApplicationScoped
@IfBuildProperty(name = "nop.graphql.json-rpc.enabled", stringValue = "true", enableIfMissing = true) // 条件注解
public class QuarkusJsonRpcWebService extends GraphQLWebService {

    @POST
    @Path("/jsonrpc")
    public CompletionStage<Response> jsonRpcSpring(@Context RoutingContext routingContext, String body) {
        return withRoutingContext(routingContext, () -> {
            return runJsonRpc(body, getHeaders()).thenApply(ret -> {
                ret.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                return JaxrsHelper.buildJaxrsResponse(ret.getHeaders(), ret.getData(), ret.getHttpStatus());
            });
        });
    }
}
