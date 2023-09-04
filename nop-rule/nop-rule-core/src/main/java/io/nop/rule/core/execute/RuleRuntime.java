package io.nop.rule.core.execute;

import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.RuleLogMessage;
import io.nop.xlang.api.XLang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RuleRuntime implements IRuleRuntime {
    private final IEvalScope scope;

    private final List<RuleLogMessage> logMessages = new ArrayList<>();

    private final Map<String, List<Object>> outputLists = new HashMap<>();

    private final Map<String, Object> outputs = new LinkedHashMap<>();

    private Map<String, Object> inputs;

    public RuleRuntime(IEvalScope scope) {
        this.scope = scope == null ? XLang.newEvalScope() : scope.newChildScope();
        this.scope.setLocalValue(RuleConstants.VAR_RULE_RT, this);
    }

    public RuleRuntime() {
        this(XLang.newEvalScope());
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    @Override
    public Map<String, List<Object>> getOutputLists() {
        return outputLists;
    }

    @Override
    public void addOutput(String name, Object value) {
        List<Object> list = outputLists.get(name);
        if (list == null) {
            list = new ArrayList<>();
            outputLists.put(name, list);
        }
        list.add(value);

        outputs.put(name, value);
    }

    public void clearOutputs() {
        outputLists.clear();
        outputs.clear();
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }

    @Override
    public Map<String, Object> getInputs() {
        return inputs;
    }

    @Override
    public Map<String, Object> getOutputs() {
        return outputs;
    }

    @Override
    public void setOutput(String name, Object value) {
        outputs.put(name, value);
    }

    @Override
    public void logMessage(String message, String ruleId, String ruleLabel) {
        RuleLogMessage logMessage = new RuleLogMessage();
        logMessage.setLogTime(CoreMetrics.currentTimeMillis());
        logMessage.setMessage(message);
        logMessage.setRuleId(ruleId);
        logMessage.setRuleLabel(ruleLabel);

        logMessages.add(logMessage);
    }

    @Override
    public List<RuleLogMessage> getLogMessages() {
        return logMessages;
    }
}