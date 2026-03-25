package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import java.util.concurrent.CompletionStage;

public class MoveFileExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "move-file";

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

            boolean overwrite = call.attrBoolean("overwrite", false);

            fs.move(source, target, overwrite);

            return AiToolCallResult.successResult(call.getId(), 
                "File moved successfully from " + source + " to " + target);
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e.getMessage());
        }
    }
}
