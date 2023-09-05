package io.nop.rule.core.service;

import io.nop.rule.api.beans.RuleLogMessageBean;
import io.nop.rule.core.IRuleRuntime;

import java.util.List;

public interface IRuleLogMessageSaver {
    void saveLogMessages(List<RuleLogMessageBean> messages, IRuleRuntime ruleRt);
}
