/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.FileResource;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.IXDslModel;
import io.nop.xlang.xdsl.XDslKeys;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

import static io.nop.cli.CliErrors.ARG_PATH;
import static io.nop.cli.CliErrors.ERR_CLI_MODEL_OBJECT_NO_XDSL_SCHEMA;

@CommandLine.Command(
    name = "extract",
    mixinStandardHelpOptions = true,
    description = "Extract model objects defined in an xlsx file and write to an external file"
)
public class CliExtractCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file (default: out.json)")
    File outputFile;

    @CommandLine.Option(names = {"-t", "--template"}, description = "Parsing template path")
    String templatePath;

    @CommandLine.Option(names = {"-f", "--format"}, description = "Output format: xml|json")
    OutputFormat format;

    @CommandLine.Parameters(description = "Model xlsx file", index = "0")
    String xlsxFile;

    enum OutputFormat {
        xml,
        json
    }

    @Override
    public Integer call() {
        IResource resource = ResourceHelper.resolveRelativePathResource(xlsxFile);

        File outputFile = this.outputFile;
        if (outputFile == null) {
            if (format == OutputFormat.xml) {
                outputFile = new File("out.xml");
            } else {
                outputFile = new File("out.json");
            }
        }

        if (format == null) {
            String fileName = outputFile.getName();
            if (fileName.endsWith(".xml")) {
                format = OutputFormat.xml;
            } else {
                format = OutputFormat.json;
            }
        }

        IComponentModel model = parseModel(resource);
        if (format == OutputFormat.xml) {
            String xdefPath = null;
            if (model instanceof IXDslModel) {
                xdefPath = ((IXDslModel) model).getXdslSchema();
            } else if (model instanceof DynamicObject) {
                xdefPath = (String) ((DynamicObject) model).prop_get(XDslKeys.DEFAULT.SCHEMA);
            }
            if (StringHelper.isEmpty(xdefPath))
                throw new NopException(ERR_CLI_MODEL_OBJECT_NO_XDSL_SCHEMA)
                        .param(ARG_PATH, resource.getPath());

            DslModelHelper.saveDslModel(xdefPath, model, new FileResource(outputFile));
        } else {
            String json = JsonTool.serialize(model, true);
            FileHelper.writeText(outputFile, json, null);
        }
        return 0;
    }

    IComponentModel parseModel(IResource resource) {
        String path = resource.getPath();
        if (path.endsWith(".xlsx")) {
            if (templatePath != null && templatePath.endsWith(".imp.xml")) {
                // Use specified imp.xml model file to parse
                return (IComponentModel) ExcelReportHelper.loadXlsxObject(templatePath, resource);
            }
        }
        return ResourceComponentManager.instance().loadComponentModel(path);
    }
}