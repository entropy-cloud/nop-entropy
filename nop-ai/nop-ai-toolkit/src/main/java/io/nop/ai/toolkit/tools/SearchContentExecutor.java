package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.SearchMatch;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class SearchContentExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "grep";

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

            String pattern = call.attrText("pattern", "");
            if (pattern.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "Pattern is required");
            }

            String path = call.attrText("path", ".");
            boolean recursive = call.attrBoolean("recursive", false);
            boolean ignoreCase = call.attrBoolean("ignoreCase", false);
            int maxMatchesPerFile = call.attrInt("maxMatchesPerFile", 100);
            int maxFiles = call.attrInt("maxFiles", 10);
            int maxDepth = call.attrInt("maxDepth", recursive ? Integer.MAX_VALUE : 1);

            List<SearchMatch> matches = fs.grep(pattern, path, recursive, ignoreCase, 
                maxMatchesPerFile, maxFiles, maxDepth);

            StringBuilder sb = new StringBuilder();
            for (SearchMatch match : matches) {
                sb.append(match.toFormattedString()).append("\n");
            }
            return AiToolCallResult.successResult(call.getId(), sb.toString());
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e.getMessage());
        }
    }
}
