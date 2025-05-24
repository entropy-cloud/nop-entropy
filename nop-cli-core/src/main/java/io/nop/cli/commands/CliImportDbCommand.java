/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.dbtool.exp.ImportDbTool;
import io.nop.dbtool.exp.config.ImportDbConfig;
import picocli.CommandLine;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "import-db",
        mixinStandardHelpOptions = true,
        description = "导入数据到数据库中"
)
public class CliImportDbCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"-i", "--input"}, description = "数据数据目录")
    File inputDir;

    @CommandLine.Option(names = {"-a", "--args"}, description = "输入参数")
    String args;

    @CommandLine.Option(names = {"-s", "--state"}, description = "状态文件路径")
    File stateFile;

    @CommandLine.Parameters(description = "配置文件路径")
    String configPath;

    @CommandLine.Option(
            names = "-P",
            description = "动态参数（格式：-Pname=value）",
            paramLabel = "KEY=VALUE"
    )
    Map<String, String> dynamicParams = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public Integer call() {
        IResource resource = ResourceHelper.resolveRelativePathResource(configPath);
        ImportDbConfig config = (ImportDbConfig) ResourceComponentManager.instance().loadComponentModel(resource.getPath());
        // 有可能会修改config的属性，所以需要复制一份
        config = config.cloneInstance();

        if (inputDir != null)
            config.setInputDir(inputDir.getAbsolutePath());

        if (config.getInputDir() == null)
            config.setInputDir(new File("data").getAbsolutePath());

        ImportDbTool tool = new ImportDbTool();
        tool.setConfig(config);
        tool.setStateFile(stateFile);

        if (args != null)
            tool.setArgs((Map<String, Object>) JsonTool.parseNonStrict(null, args));

        if(dynamicParams != null)
            dynamicParams.forEach(tool::addArg);
        tool.execute();
        return 0;
    }
}