/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.delta.JsonDiffer;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "diff",
        mixinStandardHelpOptions = true,
        description = "比较模型差异"
)
public class CliDiffCommand implements Callable<Integer> {
    static final Logger LOG = LoggerFactory.getLogger(CliDiffCommand.class);

    @CommandLine.Option(names = {"-O", "--old-model"}, required = true,
            description = "旧模型")
    String oldModelPath;

    @CommandLine.Option(names = {"-N", "--new-model"}, description = "新模型")
    String newModelPath;

    @CommandLine.Option(names = {"-f", "--format"}, description = "文件格式")
    DiffFormat format;

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出文件")
    File outputFile;

    public enum DiffFormat {
        json,
        xml,
        yaml
    }

    @Override
    public Integer call() {
        if (format == null)
            format = DiffFormat.json;

        if (outputFile == null)
            outputFile = new File("diff." + format);

        try {
            Object oldModel = loadModel(oldModelPath);
            Object newModel = loadModel(newModelPath);

            Map<String, Object> oldJson = (Map<String, Object>) JsonTool.serializeToJson(oldModel);
            Map<String, Object> newJson = (Map<String, Object>) JsonTool.serializeToJson(newModel);

            Map<String, Object> diff = new JsonDiffer().diffMap(oldJson, newJson);
            saveOutput(diff);
        } catch (Exception e) {
            LOG.error("nop.cli.diff-fail", e);
            throw NopException.adapt(e);
        }

        return 0;
    }

    Object loadModel(String modelPath) {
        IResource resource = ResourceHelper.resolveRelativePathResource(newModelPath);
        String fileExt = StringHelper.fileExt(modelPath);
        if (JsonTool.isJsonOrYamlFileExt(fileExt))
            return JsonTool.parseBeanFromResource(resource);
        return ResourceComponentManager.instance().loadComponentModel(resource.getPath());
    }

    void saveOutput(Map<String, Object> diff) {
        String text = null;
        if (format == DiffFormat.yaml) {
            text = JsonTool.serializeToYaml(diff);
        } else {
            text = JsonTool.serialize(diff, true);
        }
        FileHelper.writeText(outputFile, text, null);
    }
}