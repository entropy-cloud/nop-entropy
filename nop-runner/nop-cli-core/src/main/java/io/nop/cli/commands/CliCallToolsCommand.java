/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.executor.ToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.LocalToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.commons.util.FileHelper;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

    @CommandLine.Parameters(description = "XML content (or use -i for file input)", arity = "0..1")
    String xmlContent;

    @Override
    public Integer call() {
        try {
            String content = getXmlContent();
            if (content == null || content.isEmpty()) {
                LOG.error("No XML content provided. Use -i for file input or pass XML as argument.");
                return 1;
            }

            IToolManager toolManager = BeanContainer.getBeanByType(IToolManager.class);
            if (toolManager == null) {
                LOG.error("IToolManager bean not found. Make sure nop-ai-toolkit is properly configured.");
                return 1;
            }

            File workDir = baseDir == null ? new File(".") : baseDir;
            IToolFileSystem fileSystem = new LocalToolFileSystem(workDir);

            IToolExecuteContext context = ToolExecuteContext.builder()
                    .workDir(workDir)
                    .fileSystem(fileSystem)
                    .executor(SyncThreadPoolExecutor.INSTANCE)
                    .build();

            XNode toolsNode = XNodeParser.instance().parseFromText(null, content);
            if (toolsNode == null) {
                LOG.error("Failed to parse XML content");
                return 1;
            }

            AiToolCalls calls = parseToolCalls(toolsNode);
            AiToolCallsResponse response = toolManager.callTools(calls, context).join();

            String result = buildResultXml(response);

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

    private AiToolCalls parseToolCalls(XNode toolsNode) {
        AiToolCalls calls = new AiToolCalls();
        List<AiToolCall> toolCalls = new ArrayList<>();

        for (XNode child : toolsNode.getChildren()) {
            AiToolCall call = AiToolCall.fromNode(child);
            if (call != null) {
                toolCalls.add(call);
            }
        }

        calls.setBody(toolCalls);
        return calls;
    }

    private String buildResultXml(AiToolCallsResponse response) {
        XNode resultNode = XNode.make("call-tools-result");

        List<AiToolCallResult> results = response.getResults();
        if (results != null) {
            for (AiToolCallResult result : results) {
                resultNode.appendChild(buildResultNode(result));
            }
        }

        return resultNode.xml();
    }

    private XNode buildResultNode(AiToolCallResult result) {
        XNode node = XNode.make("result");
        node.setAttr("id", result.getId());
        node.setAttr("status", result.getStatus());

        if (result.getOutput() != null && result.getOutput().getBody() != null) {
            node.content(result.getOutput().getBody());
        }

        if (result.getError() != null && result.getError().getBody() != null) {
            XNode errorNode = node.makeChild("error");
            errorNode.content(result.getError().getBody());
        }

        return node;
    }
}
