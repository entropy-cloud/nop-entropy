package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
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
                String firstKey = options.get(0).key;
                return buildSuccessResult(call, firstKey);
            }

            return buildSuccessResult(call, options.get(0).key);
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

    private AiToolCallResult buildSuccessResult(AiToolCall call, String answer) {
        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(answer);
        result.setOutput(output);
        return result;
    }
}
