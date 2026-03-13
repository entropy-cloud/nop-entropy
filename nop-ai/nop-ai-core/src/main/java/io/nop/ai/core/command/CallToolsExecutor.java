/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.command;

import io.nop.ai.core.response.XmlResponseParser;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.nop.ai.core.AiCoreErrors.ARG_COMMAND;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_COMMAND_NOT_FOUND;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_EMPTY_TOOLS_NODE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_TOOLS_NODE_PARSE_FAILED;

public class CallToolsExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(CallToolsExecutor.class);

    private final Map<String, IToolCommand> commands = new HashMap<>();

    public CallToolsExecutor() {
        registerCommand(new WriteFileCommand());
        registerCommand(new PatchFileCommand());
    }

    public void registerCommand(IToolCommand command) {
        commands.put(command.getName(), command);
    }

    public void registerCommand(IToolCommand command, String alias) {
        commands.put(alias, command);
    }


    public String execute(String xmlContent, CallToolsContext context) {
        XNode toolsNode = parseToolsNode(xmlContent);
        if (toolsNode == null) {
            throw new NopException(ERR_AI_EMPTY_TOOLS_NODE);
        }

        return executeCommands(toolsNode, context);
    }

    public String executeCommands(XNode toolsNode, CallToolsContext context) {
        XNode resultNode = XNode.make("call-tools-result");

        for (XNode child : toolsNode.getChildren()) {
            ToolCommandResult result = executeCommand(child, context);
            resultNode.appendChild(buildResultNode(result));
        }

        return resultNode.xml();
    }

    protected XNode parseToolsNode(String xmlContent) {
        try {
            XNode node = XmlResponseParser.instance().parseResponse(xmlContent);
            if (node == null) {
                throw new NopException(ERR_AI_TOOLS_NODE_PARSE_FAILED);
            }
            return node;
        } catch (Exception e) {
            LOG.error("Failed to parse tools node: {}", e.getMessage());
            throw new NopException(ERR_AI_TOOLS_NODE_PARSE_FAILED, e);
        }
    }

    protected ToolCommandResult executeCommand(XNode commandNode, CallToolsContext context) {
        String commandName = commandNode.getTagName();
        if (commandName == null) {
            return ToolCommandResult.error(null, "Command node has no tag name");
        }

        IToolCommand command = commands.get(commandName);
        if (command == null) {
            throw new NopException(ERR_AI_COMMAND_NOT_FOUND)
                    .param(ARG_COMMAND, commandName);
        }

        try {
            return command.execute(commandNode, context);
        } catch (Exception e) {
            String id = commandNode.attrText("id");
            return ToolCommandResult.error(id, e.getMessage());
        }
    }

    protected XNode buildResultNode(ToolCommandResult result) {
        XNode node = XNode.make("tool-result");

        if (result.getId() != null) {
            node.setAttr("id", result.getId());
        }
        node.setAttr("status", String.valueOf(result.getStatus()));

        if (result.getError() != null) {
            node.setAttr("error", result.getError());
        }

        if (result.getOutput() != null) {
            node.setAttr("status", String.valueOf(result.getStatus()));
        }

        return node;
    }
}
