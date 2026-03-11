/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.command;

import io.nop.core.lang.xml.XNode;

/**
 * Tool command interface for call-tools execution.
 * Each command implementation handles a specific tool operation.
 */
public interface IToolCommand {
    
    /**
     * Get the command name (e.g., "write-file", "patch-file")
     */
    String getName();
    
    /**
     * Execute the command based on the XML node configuration.
     * 
     * @param node The XML node containing command configuration
     * @param context The execution context
     * @return The result of the command execution
     */
    ToolCommandResult execute(XNode node, CallToolsContext context);
}
