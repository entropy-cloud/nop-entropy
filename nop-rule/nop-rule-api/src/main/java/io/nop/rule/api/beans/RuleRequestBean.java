package io.nop.rule.api.beans;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

@DataBean
public class RuleRequestBean {
    private String ruleName;
    private Map<String,Object> inputs;

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }
}
