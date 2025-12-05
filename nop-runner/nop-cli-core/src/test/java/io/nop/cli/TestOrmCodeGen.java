/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli;

import io.nop.cli.commands.GenOrmHelper;
import io.nop.commons.type.StdSqlType;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.TemplateGenPath;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.report.core.engine.IReportEngine;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.report.core.engine.ReportEngine;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelHelper;
import io.quarkus.arc.impl.Sets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.wildfly.common.Assert.assertTrue;

public class TestOrmCodeGen extends BaseTestCase {
    @BeforeAll
    public static void setUp() {
        System.out.println("setUp");
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void tearDown() {
        CoreInitialization.destroy();
    }

    @Test
    public void testDict() {
        IResource resource = attachmentResource("test.orm.xlsx");
        Object model = ResourceComponentManager.instance().loadComponentModel(resource.getPath());
        XNode node = DslModelHelper.dslModelToXNode("/nop/schema/orm/orm.xdef", model);
        node.dump();
    }

    @Test
    public void testSaveModel() {
        IResource resource = attachmentResource("test.api.xlsx");
        Object model = ResourceComponentManager.instance().loadComponentModel(resource.getPath());

        IReportEngine reportEngine = newReportEngine();
        ExcelWorkbook xptModel = reportEngine.buildXptModelFromImpModel("/nop/rpc/imp/api.imp.xml");
        // XNode node = DslModelHelper.dslModelToXNode("/nop/schema/excel/workbook.xdef", xptModel);
        // node.dump();

        ITemplateOutput output = reportEngine.getRendererForXptModel(xptModel, "xlsx");
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("entity", model);
        output.generateToFile(getTargetFile("output.api.xlsx"), scope);
    }

    @Test
    public void testGenOrmModel() {
        File targetFile = getTargetFile("output.orm.xlsx");

        OrmModel model = new OrmModel();
        OrmEntityModel entity = new OrmEntityModel();
        entity.setName("test");
        entity.setDisplayName("Test");

        OrmColumnModel col = new OrmColumnModel();
        col.setPropId(1);
        col.setName("sid");
        col.setCode("SID");
        col.setStdSqlType(StdSqlType.VARCHAR);
        col.setPrecision(32);
        col.setPrimary(true);
        col.setTagSet(Sets.of("seq"));
        entity.addColumn(col);
        model.addEntity(entity);

        GenOrmHelper.saveOrmToExcel(model, targetFile, false);
    }

    private IReportEngine newReportEngine() {
        ReportEngine reportEngine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put("xlsx", new XlsxReportRendererFactory());
        reportEngine.setRenderers(renderers);
        return reportEngine;
    }

    @Test
    public void testPath(){
         String path = "{!model.tagSet.@no-web}{model.tagSet.@delta}";
        TemplateGenPath genPath = new TemplateGenPath();
        genPath.push("a");
        genPath.resolveTop(XLang.newEvalScope());

        genPath.push(path);
        IEvalScope scope = XLang.newEvalScope();
        OrmEntityModel table = new OrmEntityModel();
        table.addTag("delta");
        scope.setLocalValue("model", table);

        boolean result = genPath.resolveTop(scope);
        assertTrue(result);
        assertEquals("a",genPath.getTargetPath());
    }
}
