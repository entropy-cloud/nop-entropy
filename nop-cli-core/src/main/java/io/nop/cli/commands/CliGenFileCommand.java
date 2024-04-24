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
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.docx.WordTemplate;
import io.nop.ooxml.docx.parse.WordTemplateParser;
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

import static io.nop.core.resource.ResourceHelper.resolveRelativePathResource;

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

    @CommandLine.Parameters(description = "数据文件", index = "0", arity = "0..1")
    String file;

    @CommandLine.Option(names = {"-i", "--input"}, description = "输入数据")
    String input;

    @Override
    public Integer call() {
        Map<String, Object> json = null;
        if (file != null) {
            json = JsonTool.parseBeanFromResource(resolveRelativePathResource(file), Map.class);
        } else if (!StringHelper.isEmpty(input)) {
            json = (Map<String, Object>) JsonTool.parseNonStrict(input);
        } else {
            json = new HashMap<>();
        }

        File outputFile = this.outputFile;
        if (outputFile == null) {
            String fileName = file == null ? "out" : StringHelper.fileNameNoExt(file);
            if (template.endsWith(".docx")) {
                outputFile = new File(fileName + ".result.docx");
            } else {
                outputFile = new File(fileName + ".xlsx");
            }
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
            // 报表模板
            IResource tplResource = ResourceHelper.resolveRelativePathResource(template);
            ExcelWorkbook xptModel = new XptModelLoader().loadModelFromResource(tplResource);
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(null, XptConstants.VAR_ENTITY, json);

            String renderType = StringHelper.fileExt(outputFile.getName());
            newReportEngine().getRendererForXptModel(xptModel, renderType).generateToFile(outputFile, scope);
        } else if (template.endsWith(".docx")) {
            // Word模板
            IResource tplResource = ResourceHelper.resolveRelativePathResource(template);
            WordTemplate tpl = new WordTemplateParser().parseFromResource(tplResource);
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(null, XptConstants.VAR_ENTITY, json);
            tpl.generateToFile(outputFile, scope);
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