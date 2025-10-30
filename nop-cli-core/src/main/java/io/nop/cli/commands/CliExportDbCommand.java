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
import io.nop.dbtool.exp.ExportDbTool;
import io.nop.dbtool.exp.config.ExportDbConfig;
import picocli.CommandLine;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "export-db",
    mixinStandardHelpOptions = true,
    description = "Export data of specified tables from database"
)
public class CliExportDbCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output directory")
    File outputDir;

    @CommandLine.Option(names = {"-a", "--args"}, description = "Input arguments (JSON)")
    String args;

    @CommandLine.Option(names = {"-s", "--state"}, description = "State file path")
    File stateFile;
    @CommandLine.Parameters(description = "Config file path")
    String configPath;

    @CommandLine.Option(
        names = "-P",
        description = "Dynamic parameter (format: -Pname=value)",
        paramLabel = "KEY=VALUE"
    )
    Map<String, String> dynamicParams = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public Integer call() {
        IResource resource = ResourceHelper.resolveRelativePathResource(configPath);
        ExportDbConfig config = (ExportDbConfig) ResourceComponentManager.instance().loadComponentModel(resource.getPath());
    // Clone config because exporting may modify its properties
        config = config.cloneInstance();

        if (outputDir != null)
            config.setOutputDir(outputDir.getAbsolutePath());

        if (config.getOutputDir() == null)
            config.setOutputDir(new File("data").getAbsolutePath());

        ExportDbTool tool = new ExportDbTool();
        tool.setStateFile(stateFile);
        tool.setConfig(config);
        if (args != null)
            tool.setArgs((Map<String, Object>) JsonTool.parseNonStrict(null, args));

        if (dynamicParams != null)
            dynamicParams.forEach(tool::addArg);

        tool.execute();
        return 0;
    }
}