/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rule.service;

import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rule.api.RuleService;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;

@Disabled
public class TestRuleService extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    public void testRule() {
        RuleService ruleService = graphQLEngine.makeRpcProxy(RuleService.class);

    }
}
