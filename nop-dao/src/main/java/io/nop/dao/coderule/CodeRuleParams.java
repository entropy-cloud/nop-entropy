package io.nop.dao.coderule;

import io.nop.api.core.util.IVariableScope;
import io.nop.commons.util.objects.Pair;
import io.nop.core.model.query.BeanVariableScope;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

public class CodeRuleParams {
    private final String codeRulePattern;
    private final LocalDateTime now;
    private final LongSupplier seqGenerator;
    private final IVariableScope scope;

    /**
     * 已经生成的编码字符串
     */
    private StringBuilder buf;

    /**
     * 已经解析得到的变量集合
     */
    private final List<Pair<String, Object>> vars = new ArrayList<>();

    public CodeRuleParams(String codeRulePattern, LocalDateTime now,
                          LongSupplier seqGenerator, Object bean) {
        this.codeRulePattern = codeRulePattern;
        this.now = now;
        this.seqGenerator = seqGenerator;
        this.scope = BeanVariableScope.makeScope(bean);
    }

    public void addVar(String name, Object value) {
        vars.add(Pair.of(name, value));
    }

    public String getCodeRulePattern() {
        return codeRulePattern;
    }

    public LocalDateTime getNow() {
        return now;
    }

    public LongSupplier getSeqGenerator() {
        return seqGenerator;
    }

    public IVariableScope getScope() {
        return scope;
    }

    public StringBuilder getBuf() {
        return buf;
    }

    public void setBuf(StringBuilder buf) {
        this.buf = buf;
    }

    public List<Pair<String, Object>> getVars() {
        return vars;
    }
}
