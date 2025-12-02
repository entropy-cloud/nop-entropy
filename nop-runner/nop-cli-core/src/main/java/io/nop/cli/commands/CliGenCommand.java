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
import io.nop.codegen.CodeGenConstants;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.api.XLang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "gen",
    mixinStandardHelpOptions = true,
    description = "Generate code from model file path using one or more template directories"
)
public class CliGenCommand implements Callable<Integer> {
    static final Logger LOG = LoggerFactory.getLogger(CliGenCommand.class);


    @CommandLine.Option(names = {"-t", "--template"}, required = true,
        description = "Template path(s); at least one template is required")
    String[] templates;

    @CommandLine.Option(names = {"-i", "--input"}, description = "Input parameters (JSON)")
    String input;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output directory (default: current directory)")
    File outputDir;

    @CommandLine.Option(names = {"-F", "--force"}, description = "Force overwrite existing files in output directory")
    boolean forceOverride;

    @CommandLine.Parameters(description = "Model file path")
    String file;

    @CommandLine.Option(
        names = "-P",
        description = "Dynamic parameter (format: -Pname=value)",
        paramLabel = "KEY=VALUE"
    )
    Map<String, String> dynamicParams = new HashMap<>();

    @Override
    public Integer call() {
        try {
            IResource resource = ResourceHelper.resolveRelativePathResource(file);
            IEvalScope scope = XLang.newEvalScope();
            if (input != null) {
                Map<String, Object> map = (Map<String, Object>) JsonTool.parseNonStrict(null, input);
                scope.setLocalValues(map);
            }

            if (dynamicParams != null)
                dynamicParams.forEach(scope::setLocalValue);

            scope.setLocalValue(null, CodeGenConstants.VAR_CODE_GEN_MODEL_PATH, resource.getPath());

            ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByModelPath(resource.getPath());
            if (config != null) {
                Object model = ResourceComponentManager.instance().loadComponentModel(resource.getPath());
                scope.setLocalValue(null, CodeGenConstants.VAR_CODE_GEN_MODEL, model);
            } else {
                LOG.warn("nop.cli.unregistered-code-gen-model-type:{}", StringHelper.fileFullName(resource.getPath()));
            }

            File output = this.outputDir;
            if (output == null)
                output = FileHelper.currentDir();

            for (String template : templates) {
                renderTemplate(scope, template, output);
            }
        } catch (Exception e) {
            LOG.error("nop.cli.exec-fail", e);
            throw NopException.adapt(e);
        }

        return 0;
    }

    private void renderTemplate(IEvalScope scope, String template, File outputDir) {
        int pos = template.indexOf('=');
        if (pos > 0) {
            // If template format is xxx=/nop/templates/tpl then output directory becomes outputDir+'/'+xxx
            // You can use prefix before '=' to generate into a subdirectory of outputDir
            outputDir = new File(outputDir, template.substring(0, pos));
            template = template.substring(pos + 1);
        }
        if (template.startsWith("v:"))
            template = template.substring("v:".length());

        LOG.info("nop.cli.render-template:{}", template);
        IResource tplResource = VirtualFileSystem.instance().getResource(template);

        String targetPath = FileHelper.getFileUrl(outputDir);
        XCodeGenerator gen = new XCodeGenerator(tplResource.getStdPath(), targetPath);
        gen.forceOverride(forceOverride);
        gen.execute("/", scope);
    }
}