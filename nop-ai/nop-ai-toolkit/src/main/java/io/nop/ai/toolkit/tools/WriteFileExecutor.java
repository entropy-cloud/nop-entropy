package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import java.util.concurrent.CompletionStage;

public class WriteFileExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "write-file";

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

            String path = call.attrText("path", "");
            if (path.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "Path is required");
            }

            boolean append = call.attrBoolean("append", false);
            String content = call.childText("input", "");

            fs.writeText(path, content, append);

            String message = append 
                ? "Content appended to file: " + path
                : "File written successfully: " + path;
            return AiToolCallResult.successResult(call.getId(), message, path);
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e.getMessage());
        }
    }
}
