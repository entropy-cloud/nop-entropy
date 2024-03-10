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
        description = "读取xlsx文件中定义的模型对象，输出到外部文件中"
)
public class CliExtractCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出文件，缺省输出到out.json文件中")
    File outputFile;

    @CommandLine.Option(names = {"-f", "--format"}, description = "输出格式")
    OutputFormat format;

    @CommandLine.Parameters(description = "模型文件名", index = "0")
    String xlsxFile;

    enum OutputFormat {
        xml,
        json
    }

    @Override
    public Integer call() {
        IResource resource = ResourceHelper.resolveRelativePathResource(xlsxFile);

        if (format == null)
            format = OutputFormat.json;

        File outputFile = this.outputFile;
        if (outputFile == null) {
            if (format == OutputFormat.xml) {
                outputFile = new File("out.xml");
            } else {
                outputFile = new File("out.json");
            }
        }


        IComponentModel model = ResourceComponentManager.instance().loadComponentModel(resource.getPath());
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
}