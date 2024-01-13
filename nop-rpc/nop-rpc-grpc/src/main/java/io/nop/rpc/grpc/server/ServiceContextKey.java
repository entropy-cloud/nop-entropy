package io.nop.rpc.grpc.server;

import io.grpc.Context;
import io.nop.api.core.context.IContext;

public class ServiceContextKey {
    public static Context.Key<IContext> CONTEXT_KEY = Context.key("nop-context");
}
