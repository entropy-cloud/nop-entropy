package io.nop.ai.toolkit.tools;

import io.nop.api.core.time.CoreMetrics;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.json.JsonTool;
import io.nop.jq.IJsonQuery;
import io.nop.jq.JqEngine;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * AI Tool executor for jq-style JSON queries. Allows AI Agents to query and
 * transform JSON data using jq expressions via the nop-jq engine.
 *
 * <p>Resource-guard wiring (R1.3 / P1-1.9-001): the context cancel token
 * cancels the running computation (interrupt reaches the jq evaluation
 * checkpoints), the context deadline is enforced inside jq evaluation and
 * fail-fast at entry, and {@code $ENV}/{@code env} only see the sandboxed
 * context envs — never the full process environment.
 */
public class JqToolExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "jq";

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall request, IToolExecuteContext context) {
        CompletableFuture<AiToolCallResult> future = context.getExecutor().submit(
                () -> doExecute(request, context));

        ICancelToken cancelToken = context.getCancelToken();
        if (cancelToken != null) {
            // cancel(true): interrupt the worker thread so jq evaluation
            // checkpoints (checkOutputLimit) terminate the computation.
            Consumer<String> onCancel = reason -> future.cancel(true);
            cancelToken.appendOnCancel(onCancel);
            future.whenComplete((result, error) -> cancelToken.removeOnCancel(onCancel));
        }
        return future;
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

            long expireAt = context.getExpireAt();
            if (expireAt > 0 && CoreMetrics.currentTimeMillis() > expireAt) {
                return AiToolCallResult.errorResult(call.getId(), "jq tool call deadline already exceeded");
            }

            Object root = JsonTool.parse(data);
            IJsonQuery query = JqEngine.compile(expression);
            List<Object> results = query.apply(root, context.getEnvs(), expireAt);

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
        } catch (io.nop.jq.runtime.JqCancellationException e) {
            // cancellation/deadline is host-level resource control, report as termination
            return AiToolCallResult.errorResult(call.getId(), e.errorMessage());
        } catch (io.nop.jq.runtime.JqHaltException e) {
            // halt terminates the jq program uncatchably; report termination to the agent
            return AiToolCallResult.errorResult(call.getId(), e.errorMessage());
        } catch (io.nop.jq.runtime.JqRuntimeException e) {
            // the jq error value is the message the AI agent should see
            return AiToolCallResult.errorResult(call.getId(), e.errorMessage());
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e.toString());
        }
    }
}
