/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.grpc.server;

import io.grpc.Context;
import io.nop.api.core.context.IContext;

public class ServiceContextKey {
    public static Context.Key<IContext> CONTEXT_KEY = Context.key("nop-context");
}
