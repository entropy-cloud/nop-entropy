package io.nop.rule.core.execute;

import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.unittest.VarCollector;
import io.nop.rule.api.beans.RuleLogMessageBean;
import io.nop.rule.core.IRuleRuntime;
import io.nop.rule.core.RuleConstants;
import io.nop.xlang.api.XLang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RuleRuntime implements IRuleRuntime {
    static final Logger LOG = LoggerFactory.getLogger(RuleRuntime.class);
    private final IEvalScope scope;

    private final List<RuleLogMessageBean> logMessages = new ArrayList<>();

    private final Map<String, List<Object>> outputLists = new HashMap<>();

    private final Map<String, Object> outputs = new LinkedHashMap<>();

    private Map<String, Object> inputs;

    private String ruleName;

    private Integer ruleVersion;
    private boolean ruleMatch;

    private boolean collectLogMessage;

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
    public boolean isCollectLogMessage() {
        return collectLogMessage;
    }

    @Override
    public void setCollectLogMessage(boolean collectLogMessage) {
        this.collectLogMessage = collectLogMessage;
    }

    @Override
    public boolean isRuleMatch() {
        return ruleMatch;
    }

    @Override
    public void setRuleMatch(boolean ruleMatch) {
        this.ruleMatch = ruleMatch;
    }

    @Override
    public String getRuleName() {
        return ruleName;
    }

    @Override
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    @Override
    public Integer getRuleVersion() {
        return ruleVersion;
    }

    @Override
    public void setRuleVersion(Integer ruleVersion) {
        this.ruleVersion = ruleVersion;
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
    public void logMessage(String message, String ruleNodeId, String ruleNodeLabel) {
        addToLogFile(message, ruleNodeId, ruleNodeLabel);

        if (collectLogMessage) {
            RuleLogMessageBean logMessage = new RuleLogMessageBean();
            logMessage.setLogTime(CoreMetrics.currentTimestamp());
            VarCollector.instance().collectVar("rule-log-time",logMessage.getLogTime());
            logMessage.setMessage(message);
            logMessage.setRuleNodeId(ruleNodeId);
            logMessage.setRuleNodeLabel(ruleNodeLabel);

            logMessages.add(logMessage);
        }
    }

    protected void addToLogFile(String message, String ruleNodeId, String ruleNodeLabel) {
        LOG.info("rule-log:message={},ruleNodeId={},ruleNodeLabel={}", message, ruleNodeId, ruleNodeLabel);
    }

    @Override
    public List<RuleLogMessageBean> getLogMessages() {
        return logMessages;
    }
}