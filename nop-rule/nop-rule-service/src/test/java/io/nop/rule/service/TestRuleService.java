package io.nop.rule.service;

import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rule.api.RuleService;

import javax.inject.Inject;

public class TestRuleService extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    public void testRule() {
        RuleService ruleService = graphQLEngine.makeRpcProxy(RuleService.class);

    }
}
