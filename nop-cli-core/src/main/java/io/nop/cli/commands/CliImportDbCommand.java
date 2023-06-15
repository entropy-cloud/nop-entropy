/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli.commands;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.dbtool.exp.ImportDbTool;
import io.nop.dbtool.exp.config.ImportDbConfig;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "import-db",
        mixinStandardHelpOptions = true,
        description = "导入数据到数据库中"
)
public class CliImportDbCommand implements Callable<Integer> {
    @CommandLine.Parameters(description = "配置文件路径", index = "0")
    String configPath;

    @CommandLine.Option(names = {"-i", "--input"}, description = "数据数据目录")
    File inputDir;

    @Override
    public Integer call() {
        IResource resource = ResourceHelper.resolveRelativePathResource(configPath);
        ImportDbConfig config = JsonTool.parseBeanFromResource(resource, ImportDbConfig.class);
        if (inputDir != null)
            config.setInputDir(inputDir.getAbsolutePath());

        if (config.getInputDir() == null)
            config.setInputDir(new File("input").getAbsolutePath());

        ImportDbTool tool = new ImportDbTool();
        tool.setConfig(config);
        tool.execute();
        return 0;
    }
}