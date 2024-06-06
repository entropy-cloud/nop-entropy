/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.impl;

import io.nop.api.core.util.CloneHelper;
import io.nop.commons.util.FileHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestImportExcelModel extends BaseTestCase {
    @BeforeAll
    public static void beforeAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void afterAll() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParse() {
        IResource resource = attachmentResource("test_imp.test.xlsx");
        Object bean = ExcelHelper.loadXlsxObject("/nop/test/imp/test.imp.xml", resource);
        assertEquals(attachmentJsonText("imp-result.json"), JsonTool.serialize(bean, true));
    }


    /**
     * 可以解析两种不同的Excel模板格式
     */
    @Test
    public void testParse2() {
        IResource resource = attachmentResource("test_imp2.test.xlsx");
        Object bean = ExcelHelper.loadXlsxObject("/nop/test/imp/test.imp.xml", resource);
        assertEquals(attachmentJsonText("imp-result.json"), JsonTool.serialize(bean, true));

        // 报表导出的时候会对tree table数据进行转换，因此bean的属性会被修改
        Object bean2 = CloneHelper.deepClone(bean);

        ExcelReportHelper.saveXlsxObject("/nop/test/imp/test.imp.xml", getTargetResource("test-exp.xlsx"), bean);

        String html = ExcelReportHelper.getHtmlForXlsxObject("/nop/test/imp/test.imp.xml", bean2);
        ResourceHelper.writeText(getTargetResource("test-exp.html"), html);
    }


    /**
     * 动态展开的列
     */
    @Test
    public void testParse3() {
        IResource resource = attachmentResource("test_imp3.test.xlsx");
        Object bean = ExcelHelper.loadXlsxObject("/nop/test/imp/test3.imp.xml", resource);
        assertEquals(attachmentJsonText("imp-result3.json"), JsonTool.serialize(bean, true));

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("indexYears", Arrays.asList(2001, 2002, 2003, 2004));

        ExcelReportHelper.saveXlsxObject("/nop/test/imp/test3.imp.xml", getTargetResource("test-exp3.xlsx"), bean, scope);

        String html = ExcelReportHelper.getHtmlForXlsxObject("/nop/test/imp/test3.imp.xml", bean, scope);
        FileHelper.writeText(getTargetFile("test-exp3.html"), html, null);
        assertEquals(normalizeCRLF(attachmentText("test-exp3.html")), normalizeCRLF(html));
    }

    /**
     * 条件样式
     */
    @Test
    public void testParse4() {
        IResource resource = attachmentResource("test_imp4.test.xlsx");
        Object bean = ExcelHelper.loadXlsxObject("/nop/test/imp/test4.imp.xml", resource);
        assertEquals(attachmentJsonText("imp-result4.json"), JsonTool.serialize(bean, true));

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("indexYears", Arrays.asList(2001, 2002, 2003, 2004));

        ExcelReportHelper.saveXlsxObject("/nop/test/imp/test4.imp.xml", getTargetResource("test-exp4.xlsx"), bean, scope);

    }

    /**
     * 多个子表都具有动态展开的列
     */
    @Test
    public void testParse5() {
        IResource resource = attachmentResource("test_imp5.test.xlsx");
        Object bean = ExcelHelper.loadXlsxObject("/nop/test/imp/test5.imp.xml", resource);

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("indexYears", Arrays.asList(2001, 2002, 2003, 2004));
        scope.setLocalValue("jYears", Arrays.asList(2021, 2022, 2023, 2024, 2025));

        String html = ExcelReportHelper.getHtmlForXlsxObject("/nop/test/imp/test5.imp.xml", bean, scope);
        FileHelper.writeText(getTargetFile("test-exp5.html"), html, null);
        assertEquals(normalizeCRLF(attachmentText("test-exp5.html")), normalizeCRLF(html));

    }

    /**
     * 导入导出具有多层表头的子表
     */
    @Test
    public void testImportGroupHeader() {
        IResource resource = attachmentResource("test-group-header.test.xlsx");
        Object bean = ExcelHelper.loadXlsxObject("/nop/test/imp/test-group-header.imp.xml", resource);

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("indexYears", Arrays.asList(2001, 2002));
        scope.setLocalValue("groups", Arrays.asList("分组1", "分组2"));

        String html = ExcelReportHelper.getHtmlForXlsxObject("/nop/test/imp/test-group-header.imp.xml", bean, scope);
        FileHelper.writeText(getTargetFile("test-group-header.html"), html, null);
        assertEquals(normalizeCRLF(attachmentText("test-group-header.html")), normalizeCRLF(html));

    }

    @Test
    public void testTreeMerge(){
        IResource resource = attachmentResource("test-tree-merge.test.xlsx");
        Object bean = ExcelHelper.loadXlsxObject("/nop/test/imp/test-tree-merge.imp.xml", resource);

        System.out.println(JsonTool.serialize(bean,true));
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("indexYears", Arrays.asList(2001, 2002));
        scope.setLocalValue("groups", Arrays.asList("分组1", "分组2"));

        String html = ExcelReportHelper.getHtmlForXlsxObject("/nop/test/imp/test-tree-merge.imp.xml", bean, scope);
        FileHelper.writeText(getTargetFile("test-tree-merge.html"), html, null);
        assertEquals(normalizeCRLF(attachmentText("test-tree-merge.html")), normalizeCRLF(html));
    }
}
