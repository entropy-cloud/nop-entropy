package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.fs.FileInfo;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class SearchFilesExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "glob";

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

            String pattern = call.attrText("pattern", "*");
            String directory = call.attrText("directory");
            boolean recursive = call.attrBoolean("recursive", false);
            int maxResults = call.attrInt("maxResults", 100);
            int maxDepth = call.attrInt("maxDepth", recursive ? Integer.MAX_VALUE : 1);

            List<FileInfo> files = fs.glob(pattern, directory, recursive, maxDepth, maxResults);

            StringBuilder sb = new StringBuilder();
            for (FileInfo file : files) {
                sb.append(file.getPath()).append("\n");
            }
            return AiToolCallResult.successResult(call.getId(), sb.toString());
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e.getMessage());
        }
    }
}
