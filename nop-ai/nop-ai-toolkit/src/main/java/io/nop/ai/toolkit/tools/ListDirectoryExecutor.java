package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.fs.FileInfo;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class ListDirectoryExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "list-dir";

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        return context.getExecutor().submit(() -> doExecute(call, context));
    }

    private AiToolCallResult doExecute(AiToolCall call, IToolExecuteContext context) {
        try {
            IToolFileSystem fs = context.getFileSystem();
            if (fs == null) {
                return AiToolCallResult.errorResult(call.getId(), "File system not available");
            }

            String path = call.attrText("path", ".");
            boolean recursive = call.attrBoolean("recursive", false);
            int maxDepth = call.attrInt("maxDepth", recursive ? Integer.MAX_VALUE : 1);
            int maxCount = 100;

            List<FileInfo> files = fs.listDirectory(path, maxDepth, maxCount);

            StringBuilder sb = new StringBuilder();
            for (FileInfo file : files) {
                sb.append(file.toFormattedString()).append("\n");
            }
            return AiToolCallResult.successResult(call.getId(), sb.toString());
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e.getMessage());
        }
    }
}
