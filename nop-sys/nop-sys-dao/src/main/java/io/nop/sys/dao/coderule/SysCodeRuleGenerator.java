/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.dao.coderule;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.ISysCalendar;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.coderule.ICodeRule;
import io.nop.dao.coderule.ICodeRuleGenerator;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.sys.dao.entity.NopSysCodeRule;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.function.LongSupplier;

import static io.nop.sys.dao.NopSysErrors.ARG_RULE_NAME;
import static io.nop.sys.dao.NopSysErrors.ERR_SYS_UNKNOWN_CODE_RULE;

public class SysCodeRuleGenerator implements ICodeRuleGenerator {
    private ISysCalendar sysCalendar;
    private ICodeRule codeRule;

    private ISequenceGenerator sequenceGenerator;

    @Inject
    IDaoProvider daoProvider;


    @Inject
    public void setSysCalendar(ISysCalendar sysCalender) {
        this.sysCalendar = sysCalender;
    }

    @Inject
    public void setCodeRule(ICodeRule codeRule) {
        this.codeRule = codeRule;
    }

    @Inject
    public void setSequenceGenerator(ISequenceGenerator sequenceGenerator) {
        this.sequenceGenerator = sequenceGenerator;
    }

    @Override
    public String generate(String ruleName, Object bean) {
        NopSysCodeRule example = new NopSysCodeRule();
        example.setName(ruleName);
        NopSysCodeRule rule = daoProvider.daoFor(NopSysCodeRule.class).findFirstByExample(example);
        if (rule == null) {
            throw new NopException(ERR_SYS_UNKNOWN_CODE_RULE)
                    .param(ARG_RULE_NAME, ruleName);
        }

        LocalDateTime now = sysCalendar.getSysDateTime();
        String seqName = rule.getSeqName() == null ? rule.getName() : rule.getSeqName();
        LongSupplier seqGenerator = () -> sequenceGenerator.generateLong(seqName, false);
        return codeRule.generate(rule.getCodePattern(), now, seqGenerator, bean);
    }
}
