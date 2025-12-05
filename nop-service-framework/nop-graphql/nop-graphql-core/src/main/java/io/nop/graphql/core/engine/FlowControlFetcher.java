/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.rpc.api.flowcontrol.FlowControlEntry;
import io.nop.rpc.api.flowcontrol.IFlowControlRunner;

import java.util.Map;

public class FlowControlFetcher implements IDataFetcher {
    private final IFlowControlRunner runner;
    private final IDataFetcher fetcher;

    public FlowControlFetcher(IFlowControlRunner runner, IDataFetcher fetcher) {
        this.runner = runner;
        this.fetcher = fetcher;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        FlowControlEntry entry = newFlowEntry(env);
        return runner.runAsync(entry, () -> FutureHelper.futureCall(() -> fetcher.get(env)));
    }

    private FlowControlEntry newFlowEntry(IDataFetchingEnvironment env) {
        FlowControlEntry entry = new FlowControlEntry();
        entry.setInBound(true);
        Map<String, Object> headers = env.getGraphQLExecutionContext().getRequestHeaders();
        entry.setOrigin(ApiHeaders.getStringHeader(headers, ApiConstants.HEADER_APP_ID));
        entry.setBizKey(ApiHeaders.getStringHeader(headers, ApiConstants.HEADER_BIZ_KEY));
        entry.setResource(env.getSelectionBean().getName());
        return entry;
    }
}
