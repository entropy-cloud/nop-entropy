/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli.commands;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.codegen.CodeGenConstants;
import io.nop.codegen.XCodeGenerator;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
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
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "gen",
        mixinStandardHelpOptions = true,
        description = "根据模型文件路径和模板文件目录生成代码"
)
public class CliGenCommand implements Callable<Integer> {
    static final Logger LOG = LoggerFactory.getLogger(CliGenCommand.class);


    @CommandLine.Option(names = {"-t", "--template"},
            description = "模板文件路径,至少需要指定一个模板。")
    String[] templates;

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出目录，缺省为当前目录")
    File outputDir;

    @CommandLine.Option(names = {"-F", "--force"}, description = "强制覆盖输出目录中的文件")
    boolean forceOverride;

    @CommandLine.Parameters(description = "模型文件路径")
    String file;

    @Override
    public Integer call() {
        try {
            IResource resource = ResourceHelper.resolveRelativePathResource(file);
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(null, CodeGenConstants.VAR_CODE_GEN_MODEL_PATH, resource.getPath());

            ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByModelPath(resource.getPath());
            if (config != null) {
                IComponentModel model = ResourceComponentManager.instance().loadComponentModel(resource.getPath());
                scope.setLocalValue(null, CodeGenConstants.VAR_CODE_GEN_MODEL, model);
            } else {
                LOG.warn("nop.cli.unregistered-code-gen-model-type:{}", StringHelper.fileFullName(resource.getPath()));
            }

            File output = this.outputDir;
            if (output == null)
                output = new File(".");

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
            // 如果template的格式为xxx=/nop/templates/tpl这种形式，则生成目录为outputDir+'/'+xxx
            // 可以通过template参数来指定生成到outputDir的某个子目录下
            outputDir = new File(outputDir, template.substring(0, pos));
            template = template.substring(pos + 1);
        }
        if(template.startsWith("v:"))
            template = template.substring("v:".length());

        LOG.info("nop.cli.render-template:{}", template);
        IResource tplResource = VirtualFileSystem.instance().getResource(template);

        String targetPath = FileHelper.getFileUrl(outputDir);
        XCodeGenerator gen = new XCodeGenerator(tplResource.getStdPath(), targetPath);
        gen.forceOverride(forceOverride);
        gen.execute("/", scope);
    }
}