/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ResourceComponentManager;
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
        ExportDbTool tool = new ExportDbTool();
        ExportDbConfig config = (ExportDbConfig) ResourceComponentManager.instance().loadComponentModel(resource.getPath());
        // 有可能会修改config的属性，所以需要复制一份
        config = config.cloneInstance();

        tool.setConfig(config);

        if (outputDir != null)
            config.setOutputDir(outputDir.getAbsolutePath());

        if (config.getOutputDir() == null)
            config.setOutputDir(new File("data").getAbsolutePath());

        tool.execute();
        return 0;
    }
}