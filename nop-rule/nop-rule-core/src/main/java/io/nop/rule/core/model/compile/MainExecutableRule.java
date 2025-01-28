package io.nop.rule.core.model.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;

public class MainExecutableRule implements IExecutableRule, ISourceLocationGetter {
    private final SourceLocation loc;
    private final String ruleName;
    private final Long ruleVersion;
    private final IExecutableRule rule;

    public MainExecutableRule(SourceLocation loc, String ruleName, Long ruleVersion, IExecutableRule rule) {
        this.loc = loc;
        this.ruleName = ruleName;
        this.ruleVersion = ruleVersion;
        this.rule = rule;
    }

    @Override
    public SourceLocation getLocation() {
        return loc;
    }

    @Override
    public boolean execute(IRuleRuntime ruleRt) {
        if (ruleRt.getRuleName() == null)
            ruleRt.setRuleName(ruleName);
        if (ruleRt.getRuleVersion() == null)
            ruleRt.setRuleVersion(ruleVersion);

        try {
            return rule.execute(ruleRt);
        } catch (NopException e) {
            e.addXplStack("executeRule:ruleName=" + ruleRt.getRuleName() + ",ruleVersion=" + ruleRt.getRuleVersion());
            throw e;
        }
    }

}
