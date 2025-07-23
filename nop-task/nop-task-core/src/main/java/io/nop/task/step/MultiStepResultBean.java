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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@DataBean
public class MultiStepResultBean {
    private Map<String, StepResultBean> results = new LinkedHashMap<>();

    public Map<String, StepResultBean> getStepResultBeanMap() {
        return results;
    }

    public void setStepResultBeanMap(Map<String, StepResultBean> results) {
        if (results != null)
            this.results.putAll(results);
    }

    public List<StepResultBean> getStepResultBeans() {
        return new ArrayList<>(results.values());
    }

    public List<StepResultBean> getSuccessStepResultBeans() {
        return results.values().stream().filter(StepResultBean::isSuccess).collect(Collectors.toList());
    }

    public List<Object> getSuccessStepResultValues() {
        return results.values().stream().filter(StepResultBean::isSuccess)
                .map(StepResultBean::getResult).collect(Collectors.toList());
    }

    public List<Object> getSuccessOutputs(String varName) {
        return results.values().stream().filter(StepResultBean::isSuccess)
                .map(bean -> bean.getOutput(varName)).collect(Collectors.toList());
    }

    @JsonIgnore
    public StepResultBean getFirstSuccessResultBean() {
        for (StepResultBean result : results.values()) {
            if (result.isSuccess())
                return result;
        }
        return null;
    }

    public StepResultBean getStepResultBean(String stepName) {
        return results.get(stepName);
    }

    public Object getStepOutput(String stepName, String varName) {
        StepResultBean result = results.get(stepName);
        return result == null ? null : result.getOutput(varName);
    }

    public Object getStepResultValue(String stepName) {
        StepResultBean result = getStepResultBean(stepName);
        return result == null ? null : result.getResult();
    }

    public Number sum(String varName) {
        Number ret = 0;

        for (StepResultBean result : results.values()) {
            if (result.isSuccess()) {
                Object value = result.getOutput(varName);
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
                Object value = result.getOutput(varName);
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
