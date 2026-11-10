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

    /**
     * 3.2-C8-008（plan 2557 裁定 H）：命中上界服务端 clamp（≤1万）——模型可绕过
     * 请求面传任意大值，服务端强制收口（fail-safe，不报错）。
     */
    public static final int MAX_MATCHES_PER_FILE = 10_000;
    public static final int MAX_FILES = 10_000;

    private static final int DEFAULT_MAX_MATCHES_PER_FILE = 100;
    private static final int DEFAULT_MAX_FILES = 10;

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
            int maxMatchesPerFile = clamp(call.attrInt("maxMatchesPerFile", DEFAULT_MAX_MATCHES_PER_FILE),
                    DEFAULT_MAX_MATCHES_PER_FILE, MAX_MATCHES_PER_FILE);
            int maxFiles = clamp(call.attrInt("maxFiles", DEFAULT_MAX_FILES),
                    DEFAULT_MAX_FILES, MAX_FILES);
            int maxDepth = call.attrInt("maxDepth", recursive ? Integer.MAX_VALUE : 1);

            List<SearchMatch> matches = fs.grep(pattern, path, recursive, ignoreCase, 
                maxMatchesPerFile, maxFiles, maxDepth);

            StringBuilder sb = new StringBuilder();
            for (SearchMatch match : matches) {
                sb.append(match.toFormattedString()).append("\n");
            }
            return AiToolCallResult.successResult(call.getId(), sb.toString());
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), String.valueOf(e));
        }
    }

    /**
     * 3.2-C8-008（plan 2557 裁定 H）：非正值回落缺省，超帽截到帽——上界只在服务端生效。
     */
    static int clamp(int requested, int defaultValue, int max) {
        if (requested <= 0) {
            return defaultValue;
        }
        return Math.min(requested, max);
    }
}
