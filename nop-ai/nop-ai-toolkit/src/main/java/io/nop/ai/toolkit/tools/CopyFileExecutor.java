package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import java.util.concurrent.CompletionStage;

public class CopyFileExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "copy-file";

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

            String source = call.attrText("source", "");
            String target = call.attrText("target", "");
            if (source.isEmpty() || target.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "Source and target paths are required");
            }

            boolean recursive = call.attrBoolean("recursive", false);
            boolean overwrite = call.attrBoolean("overwrite", false);

            fs.copy(source, target, recursive, overwrite);

            return AiToolCallResult.successResult(call.getId(), 
                "File copied successfully from " + source + " to " + target);
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e.getMessage());
        }
    }
}
