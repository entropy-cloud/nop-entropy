/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao.coderule;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.ISysCalendar;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.coderule.ICodeRule;
import io.nop.dao.coderule.ICodeRuleGenerator;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.sys.dao.entity.NopSysCodeRule;

import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.LongSupplier;

import static io.nop.sys.dao.NopSysDaoConfigs.CFG_SYS_CODE_RULE_CACHE_MAX_SIZE;
import static io.nop.sys.dao.NopSysDaoConfigs.CFG_SYS_CODE_RULE_CACHE_TTL;
import static io.nop.sys.dao.NopSysErrors.ARG_RULE_NAME;
import static io.nop.sys.dao.NopSysErrors.ERR_SYS_CODE_RULE_EMPTY_SEQ_NAME;
import static io.nop.sys.dao.NopSysErrors.ERR_SYS_UNKNOWN_CODE_RULE;

public class SysCodeRuleGenerator implements ICodeRuleGenerator {
    private ISysCalendar sysCalendar;
    private ICodeRule codeRule;

    private ISequenceGenerator sequenceGenerator;

    /**
     * 规则配置按名缓存（生成编码常在主键/单据号路径，规则基本不变）。
     * <ul>
     *   <li>失效：本节点的管理端修改经 {@link #clearCache()}（NopSysCodeRuleBizModel.afterEntityChange）
     *       即时失效；多节点部署下其他节点的修改依赖 TTL 超时最终一致（nop.sys.code-rule.cache-timeout）。</li>
     *   <li>上限：nop.sys.code-rule.cache-max-size，防止规则名空间异常膨胀（正常规则数=流程数级别）。</li>
     *   <li>缓存的实体为detach状态，仅读取codePattern/seqName等纯属性，无懒加载访问。</li>
     * </ul>
     */
    private final Cache<String, NopSysCodeRule> ruleCache = newBoundedRuleCache();

    @Inject
    IDaoProvider daoProvider;

    private static Cache<String, NopSysCodeRule> newBoundedRuleCache() {
        return Caffeine.newBuilder()
                .maximumSize(CFG_SYS_CODE_RULE_CACHE_MAX_SIZE.get())
                .expireAfterWrite(CFG_SYS_CODE_RULE_CACHE_TTL.get())
                .build();
    }

    /** 规则配置变更后失效缓存（管理端修改codePattern/seqName即时生效，TTL兜底跨节点）。 */
    public void clearCache() {
        ruleCache.invalidateAll();
    }

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
        NopSysCodeRule rule = ruleCache.getIfPresent(ruleName);
        if (rule == null) {
            NopSysCodeRule example = new NopSysCodeRule();
            example.setName(ruleName);
            rule = daoProvider.daoFor(NopSysCodeRule.class).findFirstByExample(example);
            if (rule == null) {
                throw new NopException(ERR_SYS_UNKNOWN_CODE_RULE)
                        .param(ARG_RULE_NAME, ruleName);
            }
            ruleCache.put(ruleName, rule);
        }

        LocalDateTime now = sysCalendar.getSysDateTime();
        String seqName = rule.getSeqName();
        if (StringHelper.isEmpty(seqName))
            throw new NopException(ERR_SYS_CODE_RULE_EMPTY_SEQ_NAME)
                    .param(ARG_RULE_NAME, ruleName);
        LongSupplier seqGenerator = () -> sequenceGenerator.generateLong(seqName, false);
        return codeRule.generate(rule.getCodePattern(), now, seqGenerator, bean);
    }
}
