/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.demo;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.build.XptModelLoader;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

@NopTestConfig(localDb = true)
public class TestReportEngine extends JunitBaseTestCase {
    @Inject
    IReportEngine reportEngine;

    @Test
    public void testUseDataInJava() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportPath = "/report/通过scope传递数据.xpt.xlsx";
        ITextTemplateOutput output = reportEngine.getHtmlRenderer(reportPath);

        IEvalScope scope = XLang.newEvalScope();
        Map<String, Object> data = classpathBean("_vfs/report/data.json5", Map.class);
        scope.setLocalValues(data);
        output.generateToFile(getTargetFile("test-scope.html"), scope);
    }

    @Test
    public void testValidateAfterExpand() {
        String reportPath = "/report/validate-after-expand.xpt.xlsx";
        ITextTemplateOutput output = reportEngine.getHtmlRenderer(reportPath);

        IEvalScope scope = XLang.newEvalScope();
        Map<String, Object> data = classpathBean("_vfs/report/data.json5", Map.class);
        scope.setLocalValues(data);
        output.generateToFile(getTargetFile("test-validate-after-expand.html"), scope);
    }


    @Test
    public void testConfig() {
        setTestConfig(ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
        String reportPath = "/nop/report/demo/base/04-交叉报表—数据双向扩展.xpt.xlsx";
        ExcelWorkbook model = new XptModelLoader().loadObjectFromPath(reportPath);
        String json = JsonTool.serialize(model, true);
        FileHelper.writeText(getTargetFile("xpt-demo.json"), json, null);
        FileHelper.writeText(getTargetFile("xpt-demo.yaml"), JsonTool.serializeToYaml(model), null);
        DslModelHelper.saveDslModel("/nop/schema/excel/workbook.xdef", model, getTargetResource("xpt-demo.xpt.xml"));
    }
}
