package io.nop.rule.web;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.web.page.PageProvider;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Map;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestNopRulePages extends JunitBaseTestCase {

    @Inject
    PageProvider pageProvider;

    @Test
    public void testSubEdit() {
        Map<String, Object> source = pageProvider.getPageSource("/nop/rule/pages/NopRuleRole/test.page.yaml");
        System.out.println(JsonTool.serialize(source, true));
    }
}
