/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.sentinel.config;

import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import io.nop.api.core.config.IConfigRefreshable;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.JsonWhitelist;
import io.nop.core.type.IGenericType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.core.type.utils.JavaGenericTypeBuilder.buildListType;

/**
 * 4.2-MA-05（plan 2559 裁定 I / C8-007）：规则热更新对称化。
 * <p>
 * ① 空规则集 = 显式清空该族（对称热更新）：修复前 isEmpty→skip 形成单向棘轮——
 * 一旦加载过规则，清空配置无法下线该族规则，只能重启。mass-clear 风险属对称
 * 热更新语义的既定取舍（裁定 I）。
 * ② 解析失败 → 结构化 WARN + 失败计数器（本类可观测承载），并保留旧规则集继续
 * 生效（不 loadRules）。可观测等效性声明：sentinel leaf 无指标面，裁定 I 取
 * 「结构化 WARN + 计数器」达成与「统一错误码+指标上报」等效的可观测；若后续
 * 批引入指标面则升级为指标上报。
 */
public class SentinelRuleConfig implements IConfigRefreshable {
    static final Logger LOG = LoggerFactory.getLogger(SentinelRuleConfig.class);

    private String flowRules;
    private String degradeRules;
    private String sysRules;
    private String authRules;

    // 裁定 I：解析失败计数器——热更新失败的可观测载体（保留旧集，不中断服务）
    private final AtomicLong flowRuleLoadFailures = new AtomicLong();
    private final AtomicLong degradeRuleLoadFailures = new AtomicLong();
    private final AtomicLong sysRuleLoadFailures = new AtomicLong();
    private final AtomicLong authRuleLoadFailures = new AtomicLong();

    static {
        JsonWhitelist.add(FlowRule.class.getName());
        JsonWhitelist.add(DegradeRule.class.getName());
        JsonWhitelist.add(AuthorityRule.class.getName());
        JsonWhitelist.add(SystemRule.class.getName());
    }

    public String getFlowRules() {
        return flowRules;
    }

    public void setFlowRules(String flowRules) {
        this.flowRules = flowRules;
    }

    public String getDegradeRules() {
        return degradeRules;
    }

    public void setDegradeRules(String degradeRules) {
        this.degradeRules = degradeRules;
    }

    public String getSysRules() {
        return sysRules;
    }

    public void setSysRules(String sysRules) {
        this.sysRules = sysRules;
    }

    public String getAuthRules() {
        return authRules;
    }

    public void setAuthRules(String authRules) {
        this.authRules = authRules;
    }

    public long getFlowRuleLoadFailures() {
        return flowRuleLoadFailures.get();
    }

    public long getDegradeRuleLoadFailures() {
        return degradeRuleLoadFailures.get();
    }

    public long getSysRuleLoadFailures() {
        return sysRuleLoadFailures.get();
    }

    public long getAuthRuleLoadFailures() {
        return authRuleLoadFailures.get();
    }

    @PostConstruct
    @Override
    public void refreshConfig() {
        // C8-007（plan 2559 裁定 I）：空规则集 = 显式清空该族（对称热更新）；
        // 解析失败 = 结构化 WARN + 计数 + 保留旧集（loadRules 不调用）。
        loadFlowRules();
        loadDegradeRules();
        loadSysRules();
        loadAuthRules();
    }

    void loadFlowRules() {
        if (StringHelper.isEmpty(flowRules)) {
            FlowRuleManager.loadRules(Collections.emptyList());
            return;
        }
        try {
            IGenericType flowRuleType = buildListType(FlowRule.class);
            List<FlowRule> rules = (List<FlowRule>) JsonTool.parseBeanFromText(flowRules, flowRuleType);
            FlowRuleManager.loadRules(rules);
        } catch (Exception e) {
            flowRuleLoadFailures.incrementAndGet();
            LOG.warn("nop.cluster.sentinel.load-flow-rule-fail:retain-old-set", e);
        }
    }

    void loadDegradeRules() {
        if (StringHelper.isEmpty(degradeRules)) {
            DegradeRuleManager.loadRules(Collections.emptyList());
            return;
        }
        try {
            IGenericType degradeRuleType = buildListType(DegradeRule.class);
            List<DegradeRule> rules = (List<DegradeRule>) JsonTool.parseBeanFromText(degradeRules, degradeRuleType);
            DegradeRuleManager.loadRules(rules);
        } catch (Exception e) {
            degradeRuleLoadFailures.incrementAndGet();
            LOG.warn("nop.cluster.sentinel.load-degrade-rule-fail:retain-old-set", e);
        }
    }

    void loadSysRules() {
        if (StringHelper.isEmpty(sysRules)) {
            SystemRuleManager.loadRules(Collections.emptyList());
            return;
        }
        try {
            IGenericType sysRuleType = buildListType(SystemRule.class);
            List<SystemRule> rules = (List<SystemRule>) JsonTool.parseBeanFromText(sysRules, sysRuleType);
            SystemRuleManager.loadRules(rules);
        } catch (Exception e) {
            sysRuleLoadFailures.incrementAndGet();
            LOG.warn("nop.cluster.sentinel.load-sys-rule-fail:retain-old-set", e);
        }
    }

    void loadAuthRules() {
        if (StringHelper.isEmpty(authRules)) {
            AuthorityRuleManager.loadRules(Collections.emptyList());
            return;
        }
        try {
            IGenericType authRuleType = buildListType(AuthorityRule.class);
            List<AuthorityRule> rules = (List<AuthorityRule>) JsonTool.parseBeanFromText(authRules, authRuleType);
            AuthorityRuleManager.loadRules(rules);
        } catch (Exception e) {
            authRuleLoadFailures.incrementAndGet();
            LOG.warn("nop.cluster.sentinel.load-auth-rule-fail:retain-old-set", e);
        }
    }
}
