package io.nop.rule.core.excel;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.unittest.BaseTestCase;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.model.RuleModel;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class TestRuleExcelParser extends BaseTestCase {

    @BeforeAll
    public static void init(){
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy(){
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/rule/test/test-table.rule.xlsx");
        RuleModel ruleModel = new RuleExcelModelParser().parseFromResource(resource);
        XNode node = DslModelHelper.dslModelToXNode(RuleConstants.XDSL_SCHEMA_RULE, ruleModel);
        node.dump();
    }
}
