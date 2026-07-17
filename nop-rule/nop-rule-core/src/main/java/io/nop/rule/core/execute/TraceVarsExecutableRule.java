package io.nop.rule.core.execute;

import io.nop.rule.core.IExecutableRule;
import io.nop.rule.core.IRuleRuntime;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TraceVarsExecutableRule implements IExecutableRule {
    private final Set<String> traceVars;
    private final IExecutableRule rule;

    public TraceVarsExecutableRule(Set<String> traceVars, IExecutableRule rule) {
        this.traceVars = traceVars;
        this.rule = rule;
    }

    @Override
    public boolean execute(IRuleRuntime ruleRt) {
        if (ruleRt.isCollectLogMessage() && traceVars != null && !traceVars.isEmpty()) {
            Map<String, Object> context = new HashMap<>();
            for (String var : traceVars) {
                Object value = ruleRt.getEvalScope().getValueByPropPath(var);
                if (value != null) {
                    context.put(var, sanitizeTraceValue(value));
                }
            }
            if (!context.isEmpty()) {
                ruleRt.setTraceContext(context);
            }
        }
        return rule.execute(ruleRt);
    }

    static Object sanitizeTraceValue(Object value) {
        if (value == null)
            return null;
        String str = value.toString();
        if (str.length() > 200)
            str = str.substring(0, 200);
        return str;
    }
}
