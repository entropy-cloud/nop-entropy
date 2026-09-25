package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.lang.json.JsonTool;
import io.nop.jq.jq.IJsonQuery;
import io.nop.jq.jq.JqEngine;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * AI Tool executor for jq-style JSON queries. Allows AI Agents to query and
 * transform JSON data using jq expressions via the nop-jq engine.
 */
public class JqToolExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "jq";

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall request, IToolExecuteContext context) {
        return context.getExecutor().submit(() -> doExecute(request, context));
    }

    private AiToolCallResult doExecute(AiToolCall call, IToolExecuteContext context) {
        try {
            String expression = call.childText("expression", "");
            if (expression.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "jq expression is required");
            }

            String data = call.childText("data", "");
            if (data.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "JSON data is required");
            }

            Object root = JsonTool.parse(data);
            IJsonQuery query = JqEngine.compile(expression);
            List<Object> results = query.apply(root);

            StringBuilder output = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                if (i > 0) output.append("\n");
                Object result = results.get(i);
                if (result == null) {
                    output.append("null");
                } else if (result instanceof String) {
                    output.append(result);
                } else {
                    output.append(JsonTool.stringify(result));
                }
            }

            return AiToolCallResult.successResult(call.getId(), output.toString());
        } catch (io.nop.jq.jq.runtime.JqRuntimeException e) {
            // the jq error value is the message the AI agent should see
            return AiToolCallResult.errorResult(call.getId(), e.errorMessage());
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e.toString());
        }
    }
}
