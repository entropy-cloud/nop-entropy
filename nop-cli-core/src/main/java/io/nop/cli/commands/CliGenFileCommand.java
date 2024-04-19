/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.XptConstants;
import io.nop.report.core.build.XptModelLoader;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.core.engine.IReportRendererFactory;
import io.nop.report.core.engine.ReportEngine;
import io.nop.report.core.engine.renderer.HtmlReportRendererFactory;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelHelper;
import picocli.CommandLine;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "gen-file",
        mixinStandardHelpOptions = true,
        description = "读取数据文件，生成Excel等报表文件"
)
public class CliGenFileCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出文件")
    File outputFile;

    @CommandLine.Option(names = {"-t", "--template"}, description = "导出模板", required = true)
    String template;

    @CommandLine.Parameters(description = "数据文件", index = "0")
    File file;

    @Override
    public Integer call() {
        Map<String, Object> json = file == null ? new HashMap<>() :
                JsonTool.parseBeanFromResource(new FileResource(file), Map.class);

        File outputFile = this.outputFile;
        if (outputFile == null) {
            String fileName = StringHelper.fileFullName(file.getName());
            outputFile = new File(fileName + ".xlsx");
        }

        if (template.endsWith(".xdef")) {
            DslModelHelper.saveDslModel(template, json, new FileResource(outputFile));
        } else if (template.endsWith(".imp.xml")) {
            ExcelWorkbook xptModel = ExcelReportHelper.buildXptModelFromImpModel(template);

            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(null, XptConstants.VAR_ENTITY, json);

            String renderType = StringHelper.fileExt(outputFile.getName());
            newReportEngine().getRendererForXptModel(xptModel, renderType).generateToFile(outputFile, scope);
        } else if (template.endsWith(".xpt.xlsx")) {
            IResource tplResource = VirtualFileSystem.instance().getResource(template);
            ExcelWorkbook xptModel = new XptModelLoader().loadModelFromResource(tplResource);
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(null, XptConstants.VAR_ENTITY, json);

            String renderType = StringHelper.fileExt(outputFile.getName());
            newReportEngine().getRendererForXptModel(xptModel, renderType).generateToFile(outputFile, scope);
        } else {
            throw new IllegalArgumentException("invalid template:" + template);
        }
        return 0;
    }

    private IReportEngine newReportEngine() {
        ReportEngine reportEngine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put(XptConstants.RENDER_TYPE_XLSX, new XlsxReportRendererFactory());
        renderers.put(XptConstants.RENDER_TYPE_HTML, new HtmlReportRendererFactory());
        reportEngine.setRenderers(renderers);
        return reportEngine;
    }
}