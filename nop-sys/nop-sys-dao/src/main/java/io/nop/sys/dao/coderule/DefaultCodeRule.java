/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao.coderule;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.dao.coderule.CodeRuleParams;
import io.nop.dao.coderule.ICodeRule;
import io.nop.dao.coderule.ICodeRuleVariable;
import io.nop.sys.dao.NopSysErrors;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import static io.nop.dao.DaoErrors.ARG_PATTERN;

public class DefaultCodeRule implements ICodeRule {
    private final Map<String, ICodeRuleVariable> variables = new ConcurrentHashMap<>();
    static final int MAX_COUNT = 20;


    public DefaultCodeRule() {
        variables.put("year", (v, params) -> pad(params.getNow().getYear(), 4));
        variables.put("month", (v, params) -> pad(params.getNow().getMonthValue(), 2));
        variables.put("dayOfMonth", (v, params) -> pad(params.getNow().getDayOfMonth(), 2));
        variables.put("hour", (v, params) -> pad(params.getNow().getHour(), 2));
        variables.put("minute", (v, params) -> pad(params.getNow().getMinute(), 2));
        variables.put("second", (v, params) -> pad(params.getNow().getSecond(), 2));
        variables.put("randNumber", this::generateRand);
        variables.put("seq", this::generateSeq);
        variables.put("prop", this::generateFromProp);
    }

    static String pad(int value, int len) {
        return StringHelper.leftPad(String.valueOf(value), len, '0');
    }

    public void addVariable(String name, ICodeRuleVariable variable) {
        variables.put(name, variable);
    }

    public void removeVariable(String name, ICodeRuleVariable variable) {
        variables.remove(name, variable);
    }

    public void setVariables(Map<String, ICodeRuleVariable> variables) {
        this.variables.putAll(variables);
    }

    protected String generateRand(String options, CodeRuleParams params) {
        int count = ConvertHelper.toPrimitiveInt(options, NopException::new);
        if (count > MAX_COUNT)
            throw new NopException(NopSysErrors.ERR_SYS_CHAR_COUNT_EXCEED_LIMIT)
                    .param(NopSysErrors.ARG_COUNT, count);

        byte[] bytes = new byte[count];
        MathHelper.secureRandom().nextBytes(bytes);
        return new BigInteger(bytes).toString().substring(0, count);
    }

    protected String generateSeq(String options, CodeRuleParams params) {
        int count = ConvertHelper.toPrimitiveInt(options, err -> new NopException(err).param(ARG_PATTERN, params.getCodeRulePattern()));
        if (count > MAX_COUNT)
            throw new NopException(NopSysErrors.ERR_SYS_CHAR_COUNT_EXCEED_LIMIT)
                    .param(NopSysErrors.ARG_COUNT, count);

        long seq = params.getSeqGenerator().getAsLong();
        String str = String.valueOf(seq);
        if (str.length() < count) {
            return StringHelper.leftPad(str, count, '0');
        } else {
            return str.substring(str.length() - count);
        }
    }

    protected String generateFromProp(String options, CodeRuleParams params) {
        int pos = options.indexOf(',');
        int len = -1;
        String propName = options;
        if (pos > 0) {
            len = ConvertHelper.toInt(options.substring(pos + 1),
                    err -> new NopException(err).param(ARG_PATTERN, params.getCodeRulePattern()));
            propName = options.substring(0, pos);
        }
        String value = StringHelper.toString(params.getScope().getValueByPropPath(propName), "");
        if (len > 0) {
            if (value.length() < len) {
                value = StringHelper.leftPad(value, len, '0');
            } else {
                value = value.substring(value.length() - len);
            }
        }
        return value;
    }

    @Override
    public String generate(String codeRulePattern, LocalDateTime dateTime, LongSupplier seqGenerator, Object bean) {
        CodeRuleParams params = new CodeRuleParams(codeRulePattern, dateTime, seqGenerator, bean);

        return StringHelper.renderTemplate2(codeRulePattern, "{@", "}", (name, buf) -> {
            int pos = name.indexOf(':');
            String options = "";
            if (pos > 0) {
                options = name.substring(pos + 1).trim();
                name = name.substring(0, pos).trim();
            }
            params.setBuf(buf);
            ICodeRuleVariable var = variables.get(name);
            if (var == null)
                throw new NopException(NopSysErrors.ERR_SYS_UNKNOWN_PREFIX_IN_CODE_RULE_PATTERN)
                        .param(NopSysErrors.ARG_PATTERN, codeRulePattern)
                        .param(NopSysErrors.ARG_PREFIX, name);
            Object value = var.resolve(options, params);
            params.addVar(name, value);
            return value;
        });
    }
}