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

import java.util.Map;
import java.util.TreeMap;

@DataBean
public class MultiStepResultBean {
    private Map<String, StepResultBean> results = new TreeMap<>();

    public Map<String, StepResultBean> getResults() {
        return results;
    }

    public void setResults(Map<String, StepResultBean> results) {
        if (results != null)
            this.results.putAll(results);
    }

    public void add(String stepName, StepResultBean result) {
        results.put(stepName, result);
    }
}
