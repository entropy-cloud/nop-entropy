/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
import java.util.List;

import static io.nop.core.type.utils.JavaGenericTypeBuilder.buildListType;

public class SentinelRuleConfig implements IConfigRefreshable {
    static final Logger LOG = LoggerFactory.getLogger(SentinelRuleConfig.class);

    private String flowRules;
    private String degradeRules;
    private String sysRules;
    private String authRules;

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

    @PostConstruct
    @Override
    public void refreshConfig() {
        if (!StringHelper.isEmpty(flowRules)) {
            try {
                IGenericType flowRuleType = buildListType(FlowRule.class);
                List<FlowRule> rules = (List<FlowRule>) JsonTool.parseBeanFromText(flowRules, flowRuleType);
                FlowRuleManager.loadRules(rules);
            } catch (Exception e) {
                LOG.error("nop.cluster.sentinel.load-flow-rule-fail", e);
            }
        }

        if (!StringHelper.isEmpty(degradeRules)) {
            try {
                IGenericType degradeRuleType = buildListType(DegradeRule.class);
                List<DegradeRule> rules = (List<DegradeRule>) JsonTool.parseBeanFromText(degradeRules, degradeRuleType);
                DegradeRuleManager.loadRules(rules);
            } catch (Exception e) {
                LOG.error("nop.cluster.sentinel.load-degrade-rule-fail", e);
            }
        }

        if (!StringHelper.isEmpty(sysRules)) {
            try {
                IGenericType sysRuleType = buildListType(SystemRule.class);
                List<SystemRule> rules = (List<SystemRule>) JsonTool.parseBeanFromText(sysRules, sysRuleType);
                SystemRuleManager.loadRules(rules);
            } catch (Exception e) {
                LOG.error("nop.cluster.sentinel.load-sys-rule-fail", e);
            }
        }

        if (!StringHelper.isEmpty(authRules)) {
            try {
                IGenericType authRuleType = buildListType(AuthorityRule.class);
                List<AuthorityRule> rules = (List<AuthorityRule>) JsonTool.parseBeanFromText(authRules, authRuleType);
                AuthorityRuleManager.loadRules(rules);
            } catch (Exception e) {
                LOG.error("nop.cluster.sentinel.load-auth-rule-fail", e);
            }
        }
    }
}
