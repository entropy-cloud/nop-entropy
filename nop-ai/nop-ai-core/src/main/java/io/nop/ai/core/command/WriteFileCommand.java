/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.command;

import io.nop.ai.core.file.FileContent;
import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WriteFileCommand implements IToolCommand {
    static final Logger LOG = LoggerFactory.getLogger(WriteFileCommand.class);

    public static final String NAME = "write-file";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolCommandResult execute(XNode node, CallToolsContext context) {
        String id = node.attrText("id");
        String path = node.attrText("path");
        String content = node.contentAsString();

        if (path == null || path.isEmpty()) {
            return ToolCommandResult.error(id, "File path is empty");
        }

        try {
            context.getFileOperator().writeFileContent(new FileContent(path, content));

            return ToolCommandResult.success(id);
        } catch (Exception e) {
            LOG.debug("nop.ai.write-file-fail:path={}", path, e);
            return ToolCommandResult.error(id, e);
        }
    }
}
