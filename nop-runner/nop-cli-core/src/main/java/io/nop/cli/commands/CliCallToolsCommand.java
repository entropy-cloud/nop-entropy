/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.ai.core.command.CallToolsContext;
import io.nop.ai.core.command.CallToolsExecutor;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "call-tools",
        mixinStandardHelpOptions = true,
        description = "Execute tool commands from XML input"
)
public class CliCallToolsCommand implements Callable<Integer> {
    static final Logger LOG = LoggerFactory.getLogger(CliCallToolsCommand.class);

    @CommandLine.Option(names = {"-d", "--base-dir"}, description = "Base directory for file operations")
    File baseDir;

    @CommandLine.Option(names = {"-i", "--input"}, description = "Input XML file path")
    File inputFile;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file path for results")
    File outputFile;

    @CommandLine.Parameters(description = "XML content (or use -i for file input)",arity = "0..1")
    String xmlContent;

    @Override
    public Integer call() {
        try {
            String content = getXmlContent();
            if (content == null || content.isEmpty()) {
                LOG.error("No XML content provided. Use -i for file input or pass XML as argument.");
                return 1;
            }

            CallToolsExecutor executor = new CallToolsExecutor();

            CallToolsContext context = new CallToolsContext(baseDir == null ? new File(".") : baseDir);

            String result = executor.execute(content, context);

            if (outputFile != null) {
                FileHelper.writeText(outputFile, result, null);
                LOG.info("Results written to: {}", outputFile.getAbsolutePath());
            } else {
                System.out.println(result);
            }

            return 0;
        } catch (Exception e) {
            LOG.error("call-tools execution failed", e);
            throw NopException.adapt(e);
        }
    }

    protected String getXmlContent() {
        if (inputFile != null) {
            return FileHelper.readText(inputFile, null);
        }
        return xmlContent;
    }
}
