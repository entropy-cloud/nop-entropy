/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.task.StepResultBean;

import java.util.LinkedHashMap;
import java.util.Map;

@DataBean
public class MultiStepResultBean {
    private Map<String, StepResultBean> results = new LinkedHashMap<>();

    public Map<String, StepResultBean> getResults() {
        return results;
    }

    public void setResults(Map<String, StepResultBean> results) {
        if (results != null)
            this.results.putAll(results);
    }

    public int size() {
        return results.size();
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    public void add(String stepName, StepResultBean result) {
        results.put(stepName, result);
    }
}
