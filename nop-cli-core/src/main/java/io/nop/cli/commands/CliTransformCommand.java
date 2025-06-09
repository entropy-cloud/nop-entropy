package io.nop.cli.commands;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.markdown.simple.MarkdownDocument;
import io.nop.ooxml.docx.parse.WordXmlHelper;
import io.nop.ooxml.markdown.DocxToMarkdownConverter;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.core.engine.IReportRendererFactory;
import io.nop.report.core.engine.ReportEngine;
import io.nop.report.core.engine.renderer.HtmlReportRendererFactory;
import io.nop.report.core.engine.renderer.SimpleHtmlReportRendererFactory;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.report.pdf.renderer.PdfReportRendererFactory;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.IXDslModel;
import io.nop.xlang.xdsl.XDslKeys;
import picocli.CommandLine;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static io.nop.cli.CliErrors.ARG_PATH;
import static io.nop.cli.CliErrors.ERR_CLI_MODEL_OBJECT_NO_XDSL_SCHEMA;
import static io.nop.report.core.XptConstants.XDSL_SCHEMA_WORKBOOK;

@CommandLine.Command(
        name = "transform",
        mixinStandardHelpOptions = true,
        description = "在DSL模型的不同格式（XML/JSON/YAML/XLSX）之间进行转换"
)
public class CliTransformCommand implements Callable<Integer> {

    @CommandLine.Parameters(description = "模型文件名", index = "0")
    String inputFile;

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出文件（可选）")
    File outputFile;

    @CommandLine.Option(names = {"-t", "--template"}, description = "仅当输入或者输出为Excel时使用）")
    String templatePath;

    @CommandLine.Option(names = {"-f", "--format"}, description = "输出格式（xml/json/xlsx）")
    OutputFormat format;

    enum OutputFormat {
        xml, json, json5, yaml, xlsx, html, pdf, shtml, md,
    }

    @Override
    public Integer call() {
        // 解析输入文件
        IResource inputResource = ResourceHelper.resolveRelativePathResource(inputFile);

        // 确定输出文件和格式
        File outputFile = determineOutputFile();
        OutputFormat format = determineOutputFormat(outputFile);

        if (inputResource.getName().endsWith(".docx")) {
            if (format == OutputFormat.md) {
                DocxToMarkdownConverter converter = new DocxToMarkdownConverter();
                MarkdownDocument doc = converter.convertFromResource(inputResource);
                FileHelper.writeText(outputFile, doc.toText(), null);
                return 0;
            } else if (format == OutputFormat.xml) {
                WordXmlHelper.loadDocxXml(inputResource).saveToResource(new FileResource(outputFile), null);
                return 0;
            }
        } else if (inputResource.getName().endsWith(".xlsx")) {
            if (format == OutputFormat.html || format == OutputFormat.shtml || format == OutputFormat.pdf) {
                // 特殊处理Excel的格式转换
                transformExcel(inputResource, format, outputFile);
                return 0;
            }

            if (format == OutputFormat.xml) {
                // 从Excel转换到xml，但是没有指定模板
                if (templatePath == null) {
                    ExcelWorkbook workbook = new ExcelWorkbookParser().parseFromResource(inputResource);
                    DslModelHelper.saveDslModel(XDSL_SCHEMA_WORKBOOK, workbook, new FileResource(outputFile));
                    return 0;
                }
            }
        }

        Object model = parseInput(inputResource);

        // 执行转换
        transformModel(model, format, outputFile);
        return 0;
    }

    private Object parseInput(IResource inputResource) {
        String path = inputResource.getPath().toLowerCase();

        if (path.endsWith(".xlsx")) {
            // Excel文件需要模板解析
            if (templatePath == null) {
                throw new IllegalArgumentException("解析Excel文件必须提供模板参数(--template)");
            }
            return ExcelReportHelper.loadXlsxObject(templatePath, inputResource);
        } else if (path.endsWith(".xml")) {
            // 解析XML格式的DSL模型
            return DslModelHelper.loadDslModel(inputResource);
        } else if (path.endsWith(".json") || path.endsWith(".json5")
                || path.endsWith(".yaml") || path.endsWith(".yml")) {
            // 解析JSON为通用Map结构
            Map<String, Object> map = JsonTool.parseBeanFromResource(inputResource, Map.class);
            return map;
        } else {
            throw new IllegalArgumentException("不支持的输入格式: " + inputResource.getPath());
        }
    }

    private File determineOutputFile() {
        if (outputFile != null) return outputFile;

        OutputFormat format = this.format;
        if (format == null)
            format = OutputFormat.json;

        // 生成默认输出文件名
        String baseName = StringHelper.fileNameNoExt(inputFile);
        String ext = "." + format.name();
        File parent = new File(inputFile).getParentFile();
        if (parent == null)
            return new File(baseName + ext);

        return new File(parent, baseName + ext);
    }

    private OutputFormat determineOutputFormat(File outputFile) {
        if (format != null) return format;

        // 根据输出文件扩展名推断格式
        String fileName = outputFile.getName().toLowerCase();
        if (fileName.endsWith(".xml")) return OutputFormat.xml;
        if (fileName.endsWith(".xlsx")) return OutputFormat.xlsx;
        if (fileName.endsWith(".json5")) return OutputFormat.json5;
        if (fileName.endsWith(".yml")) return OutputFormat.yaml;
        if (fileName.endsWith(".yaml")) return OutputFormat.yaml;
        if (fileName.endsWith(".html")) return OutputFormat.html;
        if (fileName.endsWith(".shtml")) return OutputFormat.shtml;
        if (fileName.endsWith(".pdf")) return OutputFormat.pdf;
        if (fileName.endsWith(".md")) return OutputFormat.md;

        // 默认使用JSON格式
        return OutputFormat.json;
    }

    private void transformModel(Object model, OutputFormat format, File outputFile) {
        if (format == OutputFormat.xml) {
            saveAsXml(model, outputFile);
        } else if (format == OutputFormat.xlsx) {
            saveAsXlsx(model, outputFile);
        } else if (format == OutputFormat.yaml) {
            saveAsYaml(model, outputFile);
        } else if (format == OutputFormat.json) {
            saveAsJson(model, outputFile);
        } else {
            throw new IllegalArgumentException("不支持的输出格式: " + format);
        }
    }

    private void saveAsXlsx(Object model, File outputFile) {
        if (templatePath == null) {
            throw new IllegalArgumentException("输出Excel文件必须提供模板参数(--template)");
        }

        ExcelReportHelper.saveXlsxObject(templatePath, new FileResource(outputFile), model);
    }

    private void saveAsXml(Object model, File outputFile) {
        String xdefPath = resolveXdslSchema(model);
        if (xdefPath == null) {
            throw new NopException(ERR_CLI_MODEL_OBJECT_NO_XDSL_SCHEMA)
                    .param(ARG_PATH, inputFile);
        }
        DslModelHelper.saveDslModel(xdefPath, model, new FileResource(outputFile));
    }

    private String resolveXdslSchema(Object model) {
        if (model instanceof IXDslModel) {
            return ((IXDslModel) model).getXdslSchema();
        } else if (model instanceof DynamicObject) {
            return (String) ((DynamicObject) model).prop_get(XDslKeys.DEFAULT.SCHEMA);
        }
        return null;
    }

    private void saveAsYaml(Object model, File outputFile) {
        String yaml = JsonTool.serializeToYaml(model);
        FileHelper.writeText(outputFile, yaml, null);
    }

    private void saveAsJson(Object model, File outputFile) {
        String json = JsonTool.serialize(model, true);
        FileHelper.writeText(outputFile, json, null);
    }

    private void transformExcel(IResource inputResource, OutputFormat format, File outputFile) {
        IReportEngine reportEngine = newReportEngine();
        ExcelWorkbook workbook = new ExcelWorkbookParser().parseFromResource(inputResource);
        reportEngine.getRendererForExcel(workbook, format.name()).generateToFile(outputFile, XLang.newEvalScope());
    }

    private IReportEngine newReportEngine() {
        ReportEngine reportEngine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put(XptConstants.RENDER_TYPE_XLSX, new XlsxReportRendererFactory());
        renderers.put(XptConstants.RENDER_TYPE_HTML, new HtmlReportRendererFactory());
        renderers.put(XptConstants.RENDER_TYPE_SHTML, new SimpleHtmlReportRendererFactory());
        renderers.put(XptConstants.RENDER_TYPE_PDF, new PdfReportRendererFactory());
        reportEngine.setRenderers(renderers);
        return reportEngine;
    }
}