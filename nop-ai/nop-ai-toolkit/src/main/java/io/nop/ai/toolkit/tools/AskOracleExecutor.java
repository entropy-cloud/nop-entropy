package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class AskOracleExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "ask-oracle";

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
            String question = call.childText("question");
            List<Option> options = parseOptions(call);
            int timeoutMs = call.attrInt("timeoutMs", call.getTimeoutMs() != null ? call.getTimeoutMs() : 30000);

            if (question == null || question.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "Question is required");
            }

            if (options.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "At least one option is required");
            }

            Map<String, String> envs = context.getEnvs();
            String oracleEndpoint = envs.get("ORACLE_ENDPOINT");
            if (oracleEndpoint == null || oracleEndpoint.isEmpty()) {
                // P2-MA1-011 ruling (2026-07-31): fast-fail instead of silently returning the first option.
                // A tool that returns a fabricated answer is worse than an explicit failure.
                return AiToolCallResult.errorResult(call.getId(),
                        "ask-oracle is not configured: environment variable ORACLE_ENDPOINT is missing. "
                                + "Set ORACLE_ENDPOINT to enable the oracle tool.");
            }

            // P2-MA1-011 ruling (2026-07-31): the oracle client call is not implemented yet.
            // Failing fast here (instead of returning the first option as a fake success) keeps the
            // Anti-Silent-NoOp contract: unimplemented behavior must be explicit.
            return AiToolCallResult.errorResult(call.getId(),
                    "ask-oracle oracle invocation is not implemented yet (ORACLE_ENDPOINT is set to '" + oracleEndpoint
                            + "' but no client call exists). Failing fast instead of returning a fabricated answer.");
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e);
        }
    }

    private List<Option> parseOptions(AiToolCall call) {
        List<Option> options = new ArrayList<>();
        XNode node = call.getNode();
        if (node == null) return options;

        XNode optionsNode = node.childByTag("options");
        if (optionsNode == null) return options;

        for (XNode optionNode : optionsNode.getChildren()) {
            if ("option".equals(optionNode.getTagName())) {
                String key = optionNode.attrText("key");
                String content = optionNode.contentText();
                if (key != null) {
                    options.add(new Option(key, content != null ? content.trim() : ""));
                }
            }
        }
        return options;
    }

    private static class Option {
        String key;
        String description;

        Option(String key, String description) {
            this.key = key;
            this.description = description;
        }
    }
}
