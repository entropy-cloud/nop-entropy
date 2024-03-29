/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.MathHelper;
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

    @JsonIgnore
    public StepResultBean getFirstSuccessResult() {
        for (StepResultBean result : results.values()) {
            if (result.isSuccess())
                return result;
        }
        return null;
    }

    public StepResultBean getStepResult(String stepName) {
        return results.get(stepName);
    }

    public Object getStepValue(String stepName, String varName) {
        StepResultBean result = results.get(stepName);
        return result == null ? null : result.getValue(varName);
    }

    public Number sum(String varName) {
        Number ret = 0;

        for (StepResultBean result : results.values()) {
            if (result.isSuccess()) {
                Object value = result.getValue(varName);
                if (value != null)
                    ret = MathHelper.add(ret, value);
            }
        }
        return ret;
    }

    public int count(String varName) {
        int ret = 0;

        for (StepResultBean result : results.values()) {
            if (result.isSuccess()) {
                Object value = result.getValue(varName);
                if (value != null)
                    ret++;
            }
        }
        return ret;
    }

    @JsonIgnore
    public boolean isAllSuccess() {
        for (StepResultBean result : results.values()) {
            if (!result.isSuccess())
                return false;
        }
        return true;
    }

    public int size() {
        return results.size();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return results.isEmpty();
    }

    public void add(String stepName, StepResultBean result) {
        results.put(stepName, result);
    }
}
