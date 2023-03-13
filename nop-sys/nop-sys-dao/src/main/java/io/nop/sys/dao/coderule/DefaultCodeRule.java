/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.dao.coderule;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import static io.nop.sys.dao.NopSysErrors.ARG_COUNT;
import static io.nop.sys.dao.NopSysErrors.ARG_PATTERN;
import static io.nop.sys.dao.NopSysErrors.ARG_PREFIX;
import static io.nop.sys.dao.NopSysErrors.ERR_SYS_CHAR_COUNT_EXCEED_LIMIT;
import static io.nop.sys.dao.NopSysErrors.ERR_SYS_UNKNOWN_PREFIX_IN_CODE_RULE_PATTERN;

public class DefaultCodeRule implements ICodeRule {
    private final Map<String, ICodeRuleVariable> variables = new ConcurrentHashMap<>();
    static final int MAX_COUNT = 20;


    public DefaultCodeRule() {
        variables.put("year", (v, now, g, bean) -> pad(now.getYear(), 4));
        variables.put("month", (v, now, g, bean) -> pad(now.getMonthValue(), 2));
        variables.put("dayOfMonth", (v, now, g, bean) -> pad(now.getDayOfMonth(), 2));
        variables.put("hour", (v, now, g, bean) -> pad(now.getHour(), 2));
        variables.put("minute", (v, now, g, bean) -> pad(now.getMinute(), 2));
        variables.put("second", (v, now, g, bean) -> pad(now.getSecond(), 2));
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

    protected String generateRand(String options, LocalDateTime now, LongSupplier seqGenerator, Object bean) {
        int count = ConvertHelper.toPrimitiveInt(options, NopException::new);
        if (count > MAX_COUNT)
            throw new NopException(ERR_SYS_CHAR_COUNT_EXCEED_LIMIT)
                    .param(ARG_COUNT, count);

        byte[] bytes = new byte[count];
        MathHelper.secureRandom().nextBytes(bytes);
        return new BigInteger(bytes).toString().substring(0, count);
    }

    protected String generateSeq(String options, LocalDateTime now, LongSupplier seqGenerator, Object bean) {
        int count = ConvertHelper.toPrimitiveInt(options, NopException::new);
        if (count > MAX_COUNT)
            throw new NopException(ERR_SYS_CHAR_COUNT_EXCEED_LIMIT)
                    .param(ARG_COUNT, count);

        long seq = seqGenerator.getAsLong();
        String str = String.valueOf(seq);
        if (str.length() < count) {
            return StringHelper.leftPad(str, count, '0');
        } else {
            return str.substring(str.length() - count);
        }
    }

    protected String generateFromProp(String options, LocalDateTime now, LongSupplier seqGenerator, Object bean) {
        return StringHelper.toString(BeanTool.getComplexProperty(bean, options), "");
    }

    @Override
    public String generate(String codeRulePattern, LocalDateTime dateTime, LongSupplier seqGenerator, Object bean) {
        return StringHelper.renderTemplate(codeRulePattern, "{@", "}", name -> {
            int pos = name.indexOf(':');
            String options = "";
            if (pos > 0) {
                options = name.substring(pos + 1).trim();
                name = name.substring(0, pos).trim();
            }
            ICodeRuleVariable var = variables.get(name);
            if (var == null)
                throw new NopException(ERR_SYS_UNKNOWN_PREFIX_IN_CODE_RULE_PATTERN)
                        .param(ARG_PATTERN, codeRulePattern)
                        .param(ARG_PREFIX, name);
            return var.resolve(options, dateTime, seqGenerator, bean);
        });
    }
}