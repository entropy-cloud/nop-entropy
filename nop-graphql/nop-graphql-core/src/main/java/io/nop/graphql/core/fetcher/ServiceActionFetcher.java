/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.util.Guard;
import io.nop.core.context.action.IServiceAction;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

public class ServiceActionFetcher implements IDataFetcher {
    private final IServiceAction action;

    public ServiceActionFetcher(IServiceAction action) {
        this.action = Guard.notNull(action, "action");
    }

    public IServiceAction getAction() {
        return action;
    }

    public String toString() {
        return action.toString();
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        return action.invoke(env.getArgs(), env.getSelectionBean(), env.getExecutionContext());
    }
}
