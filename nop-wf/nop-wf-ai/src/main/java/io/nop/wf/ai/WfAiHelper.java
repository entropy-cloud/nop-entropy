package io.nop.wf.ai;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.lang.json.JsonTool;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.engine.IWfRuntime;

import java.util.Map;

public final class WfAiHelper {
    private WfAiHelper() {
    }

    public static Map<String, Object> decide(String prompt, Double confidenceThreshold,
                                             String onLowConfidence, String onError, IWfRuntime wfRt) {
        try {
            Map<String, Object> result = call(prompt);
            Number confidence = (Number) result.getOrDefault("confidence", 0D);
            double threshold = confidenceThreshold == null ? 0.8D : confidenceThreshold;
            if (confidence.doubleValue() < threshold && "manual".equals(onLowConfidence)) {
                IWorkflowStep step = wfRt.getCurrentStep();
                step.changeOwnerId("manual-review", wfRt.getSvcCtx());
                return result;
            }

            Object variables = result.get("variables");
            if (variables instanceof Map) {
                Map<?, ?> vars = (Map<?, ?>) variables;
                vars.forEach((k, v) -> wfRt.getWf().getGlobalVars().setVar(String.valueOf(k), v));
            }

            String decision = String.valueOf(result.get("decision"));
            if ("PASS".equals(decision)) {
                wfRt.getCurrentStep().getRecord().setAppState("agree");
            } else if ("REJECT".equals(decision)) {
                wfRt.getCurrentStep().getRecord().setAppState("disagree");
            }
            return result;
        } catch (RuntimeException e) {
            if ("suspend".equals(onError)) {
                wfRt.getWf().suspend(null, wfRt.getSvcCtx());
                return null;
            }
            throw e;
        }
    }

    public static Map<String, Object> route(String prompt, IWfRuntime wfRt) {
        Object cached = wfRt.getWf().getGlobalVars().getVar("__aiRouteResult");
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }

        Map<String, Object> result = call(prompt);
        wfRt.getWf().getGlobalVars().setVar("__aiRouteResult", result);
        wfRt.getWf().getGlobalVars().setVar("__aiRouteDecision", result.get("decision"));
        return result;
    }

    public static Map<String, Object> extract(String prompt, IWfRuntime wfRt) {
        return route(prompt, wfRt);
    }

    public static boolean judge(String prompt, IWfRuntime wfRt) {
        Map<String, Object> result = route(prompt, wfRt);
        return "PASS".equals(String.valueOf(result.get("decision")));
    }

    private static Map<String, Object> call(String prompt) {
        IChatService chatService = BeanContainer.getBeanByType(IChatService.class);
        ChatRequest request = ChatRequest.userPrompt(prompt);
        ChatOptions options = new ChatOptions();
        options.setResponseFormat("json");
        request.setOptions(options);
        ChatResponse response = chatService.call(request, null);
        return JsonTool.parseMap(response.getFullContent());
    }
}
