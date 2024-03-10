/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.task.AsyncStepResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@DataBean
public class ParallelStateBean {
    private Map<String, AsyncStepResult> results;

    public Map<String, AsyncStepResult> getResults() {
        return results;
    }

    public void setResults(Map<String, AsyncStepResult> results) {
        this.results = results == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(results);
    }

    public void add(AsyncStepResult result) {
        results.put(result.getNextStepId(), result);
    }
}
