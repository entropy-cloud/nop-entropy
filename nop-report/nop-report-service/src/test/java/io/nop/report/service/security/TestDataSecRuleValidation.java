package io.nop.report.service.security;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.core.resource.impl.FileResource;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 2586 focused pin：数据安全规则加载期必填字段校验——缺字段 fail-fast（带规则索引），
 * 不再运行期 NPE。
 */
public class TestDataSecRuleValidation {

    File writeYaml(String content) throws Exception {
        File dir = new File("_tmp/test-data-sec-rules");
        dir.mkdirs();
        File file = new File(dir, "rules-" + System.nanoTime() + ".yaml");
        FileHelper.writeText(file, content, null);
        return file;
    }

    @Test
    public void testRowRuleMissingRowConditionRejected() throws Exception {
        NopReportDataSecurityProvider provider = new NopReportDataSecurityProvider();
        File yaml = writeYaml("rules:\n  - dsName: ds1\n");
        NopException ex = assertThrows(NopException.class,
                () -> provider.loadRules(new FileResource(yaml)));
        assertEquals(ReportDataSecErrors.ERR_REPORT_DATA_SEC_RULE_FIELD_REQUIRED.getErrorCode(),
                ex.getErrorCode());
        assertEquals(0, ex.getParam(ReportDataSecErrors.ARG_RULE_INDEX));
        assertEquals("rowCondition", ex.getParam(ReportDataSecErrors.ARG_FIELD_NAME));
    }

    @Test
    public void testMaskRuleMissingFieldAndTypeRejected() throws Exception {
        NopReportDataSecurityProvider provider = new NopReportDataSecurityProvider();
        File yaml = writeYaml("masks:\n  - dsName: ds1\n  - field: phone\n");
        // 第 0 条缺 field
        NopException ex0 = assertThrows(NopException.class,
                () -> provider.loadRules(new FileResource(yaml)));
        assertEquals(ReportDataSecErrors.ERR_REPORT_DATA_SEC_RULE_FIELD_REQUIRED.getErrorCode(),
                ex0.getErrorCode());
        assertEquals(0, ex0.getParam(ReportDataSecErrors.ARG_RULE_INDEX));
        assertEquals("field", ex0.getParam(ReportDataSecErrors.ARG_FIELD_NAME));

        File yaml2 = writeYaml("masks:\n  - field: phone\n");
        // 第 0 条缺 type
        NopException ex1 = assertThrows(NopException.class,
                () -> provider.loadRules(new FileResource(yaml2)));
        assertEquals("type", ex1.getParam(ReportDataSecErrors.ARG_FIELD_NAME));
    }

    @Test
    public void testValidRulesLoad() throws Exception {
        NopReportDataSecurityProvider provider = new NopReportDataSecurityProvider();
        File yaml = writeYaml("rules:\n  - dsName: ds1\n    rowCondition: \"a = {userId}\"\n"
                + "masks:\n  - field: phone\n    type: phone\n");
        NopReportDataSecurityProvider.DataSecRules rules = provider.loadRules(new FileResource(yaml));
        assertEquals(1, rules.rowRules.size());
        assertEquals(1, rules.maskRules.size());
    }
}
