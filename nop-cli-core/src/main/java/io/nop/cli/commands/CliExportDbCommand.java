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
import io.nop.dbtool.exp.ExportDbTool;
import io.nop.dbtool.exp.config.ExportDbConfig;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "export-db",
        mixinStandardHelpOptions = true,
        description = "导出数据库中指定表的数据"
)
public class CliExportDbCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-o", "--output"}, description = "输出目录")
    File outputDir;

    @CommandLine.Parameters(description = "配置文件路径")
    String configPath;
    @Override
    public Integer call() {
        IResource resource = ResourceHelper.resolveRelativePathResource(configPath);
        ExportDbConfig config = JsonTool.parseBeanFromResource(resource, ExportDbConfig.class);
        if (outputDir != null)
            config.setOutputDir(outputDir.getAbsolutePath());

        if (config.getOutputDir() == null)
            config.setOutputDir(new File("data").getAbsolutePath());

        ExportDbTool tool = new ExportDbTool();
        tool.setConfig(config);
        tool.execute();
        return 0;
    }
}