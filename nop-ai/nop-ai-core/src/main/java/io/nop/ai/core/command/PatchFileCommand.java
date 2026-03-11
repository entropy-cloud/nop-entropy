/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.command;

import io.nop.ai.core.file.FileContent;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.diff.UnifiedDiff;
import io.nop.diff.UnifiedDiffApplier;
import io.nop.diff.UnifiedDiffParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatchFileCommand implements IToolCommand {
    static final Logger LOG = LoggerFactory.getLogger(PatchFileCommand.class);

    public static final String NAME = "patch-file";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolCommandResult execute(XNode node, CallToolsContext context) {
        String id = node.attrText("id");
        String path = node.attrText("path");
        String diffContent = node.contentAsString();

        if (path == null || path.isEmpty()) {
            return ToolCommandResult.error(id, "File path is empty");
        }

        if (StringHelper.isEmpty(diffContent))
            return ToolCommandResult.error(id, "File patch content is empty");

        try {
            FileContent originalContent = context.getFileOperator().readFileContent(path, 0, -1);

            UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffContent);
            if (diff == null) {
                return ToolCommandResult.error(id, "Failed to parse diff content");
            }

            UnifiedDiffApplier applier = new UnifiedDiffApplier();
            String newContent = applier.apply(originalContent.getContent(), diff);

            context.getFileOperator().writeFileContent(new FileContent(path, newContent));

            return ToolCommandResult.success(id);
        } catch (Exception e) {
            LOG.debug("nop.ai.path-file-fail", e);
            return ToolCommandResult.error(id, e);
        }
    }
}
