/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx.output;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestExcelTemplate extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testGen() {
        IResource resource = new ClassPathResource("classpath:xlsx/format-demo.xlsx");
        ExcelWorkbook workbook = new ExcelWorkbookParser().parseFromResource(resource);

        ExcelOfficePackage base = ExcelOfficePackage.loadEmpty();
        ExcelTemplate tpl = new ExcelTemplate(base, workbook, null);
        tpl.indent(true);

        File target = getTargetFile("test-result");
        tpl.generateToDir(target, XLang.newEvalScope());

        tpl.generateToFile(getTargetFile("test-result.xlsx"), XLang.newEvalScope());
    }

    @Test
    public void testStyle() {
        IResource resource = new ClassPathResource("classpath:xlsx/calc.xlsx");
        ExcelWorkbook workbook = new ExcelWorkbookParser().parseFromResource(resource);

        ExcelOfficePackage base = ExcelOfficePackage.loadEmpty();
        ExcelTemplate tpl = new ExcelTemplate(base, workbook, null);
        tpl.indent(true);

        File target = getTargetFile("test-style");
        tpl.generateToDir(target, XLang.newEvalScope());

        tpl.generateToFile(getTargetFile("test-style.xlsx"), XLang.newEvalScope());
    }

    @Test
    public void testEmpty() {
        IResource resource = new ClassPathResource("classpath:nop/empty.xlsx");
        ExcelWorkbook workbook = new ExcelWorkbookParser().parseFromResource(resource);

        ExcelOfficePackage base = ExcelOfficePackage.loadEmpty();
        ExcelTemplate tpl = new ExcelTemplate(base, workbook, null);
        tpl.indent(true);

        File target = getTargetFile("test-empty");
        tpl.generateToDir(target, XLang.newEvalScope());

        tpl.generateToFile(getTargetFile("test-empty.xlsx"), XLang.newEvalScope());

        InMemoryResourceStore store = new InMemoryResourceStore();
        store.addZipFile("/", getTargetResource("test-empty.xlsx"));
        store.getAllResources("/", ".xml");
    }


    @Test
    public void testTemplate() {
        IResource resource = new ClassPathResource("classpath:xlsx/test-imp.xlsx");
        ExcelWorkbook workbook = new ExcelWorkbookParser().parseFromResource(resource);

        ExcelOfficePackage base = ExcelOfficePackage.loadEmpty();
        ExcelTemplate tpl = new ExcelTemplate(base, workbook, null);
        tpl.indent(true);

        File target = getTargetFile("test-template");
        tpl.generateToDir(target, XLang.newEvalScope());

        tpl.generateToFile(getTargetFile("test-template.xlsx"), XLang.newEvalScope());
    }

//    public static void main(String[] args) {
//        ZipFileWatcher.main(new String[]{"C:\\can\\entropy-cloud\\nop-ooxml\\nop-ooxml-xlsx\\target\\calc.xlsx"});
//    }
}
