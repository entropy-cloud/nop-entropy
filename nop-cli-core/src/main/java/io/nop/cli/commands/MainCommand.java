/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli.commands;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(
        name = "nop-cli", description = "Nop命令行工具", version = "nop-cli 2.0",
        mixinStandardHelpOptions = true,
        subcommands = {
                CliGenCommand.class,
                CliReverseDbCommand.class,
                CliWatchCommand.class,
                CliRunCommand.class,
                CliGenOrmExcelCommand.class,
                CliExtractCommand.class,
                CliExportDbCommand.class,
                CliImportDbCommand.class,
                CliGenFileCommand.class
        })
public class MainCommand {
}
