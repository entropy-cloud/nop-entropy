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
