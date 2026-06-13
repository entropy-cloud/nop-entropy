package io.nop.ai.agent.hook;

import io.nop.ai.agent.model.AgentHookModel;
import io.nop.ai.agent.model.AgentModel;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DefaultHookRegistry implements IHookRegistry {

    private static final Map<String, AgentLifecyclePoint> EVENT_NAME_MAP = buildEventNameMap();

    private final Map<AgentLifecyclePoint, List<IAgentLifecycleHook>> hooks = new EnumMap<>(AgentLifecyclePoint.class);

    public DefaultHookRegistry() {
    }

    @Override
    public List<IAgentLifecycleHook> getHooks(AgentLifecyclePoint point, String agentName) {
        List<IAgentLifecycleHook> list = hooks.get(point);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    @Override
    public void register(AgentLifecyclePoint point, IAgentLifecycleHook hook) {
        hooks.computeIfAbsent(point, k -> new ArrayList<>()).add(hook);
    }

    public static DefaultHookRegistry fromAgentModel(AgentModel model) {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        if (model == null || !model.hasHooks()) {
            return registry;
        }
        for (AgentHookModel hookModel : model.getHooks()) {
            AgentLifecyclePoint point = resolveLifecyclePoint(hookModel.getEvent());
            if (point == null) {
                continue;
            }
            IEvalFunction body = hookModel.getBody();
            registry.register(point, new EvalFunctionHookAdapter(body));
        }
        return registry;
    }

    static AgentLifecyclePoint resolveLifecyclePoint(String event) {
        if (event == null || event.isEmpty()) {
            return null;
        }
        return EVENT_NAME_MAP.get(event.toLowerCase(Locale.ROOT));
    }

    private static Map<String, AgentLifecyclePoint> buildEventNameMap() {
        Map<String, AgentLifecyclePoint> map = new java.util.HashMap<>();

        addMapping(map, "before_call", "pre_call", AgentLifecyclePoint.PRE_CALL);
        addMapping(map, "before_reasoning", "pre_reasoning", AgentLifecyclePoint.PRE_REASONING);
        addMapping(map, "after_reasoning", "post_reasoning", AgentLifecyclePoint.POST_REASONING);
        addMapping(map, "before_acting", "pre_acting", AgentLifecyclePoint.PRE_ACTING);
        addMapping(map, "after_acting", "post_acting", AgentLifecyclePoint.POST_ACTING);
        addMapping(map, "on_error", AgentLifecyclePoint.ON_ERROR);
        addMapping(map, "after_call", "post_call", AgentLifecyclePoint.POST_CALL);
        addMapping(map, "reasoning_chunk", AgentLifecyclePoint.REASONING_CHUNK);
        addMapping(map, "before_compact", "pre_compact", AgentLifecyclePoint.PRE_COMPACT);
        addMapping(map, "after_compact", "post_compact", AgentLifecyclePoint.POST_COMPACT);
        addMapping(map, "before_tool_result_processed", AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED);
        addMapping(map, "after_tool_result_processed", AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED);

        return map;
    }

    private static void addMapping(Map<String, AgentLifecyclePoint> map, String canonicalSnake,
                                   String altSnake, AgentLifecyclePoint point) {
        map.put(canonicalSnake, point);
        map.put(altSnake, point);
        map.put(point.name().toLowerCase(Locale.ROOT), point);
    }

    private static void addMapping(Map<String, AgentLifecyclePoint> map, String canonicalSnake,
                                   AgentLifecyclePoint point) {
        map.put(canonicalSnake, point);
        map.put(point.name().toLowerCase(Locale.ROOT), point);
    }

    private static class EvalFunctionHookAdapter implements IAgentLifecycleHook {
        private final IEvalFunction body;

        EvalFunctionHookAdapter(IEvalFunction body) {
            this.body = body;
        }

        @Override
        public HookResult onEvent(HookContext ctx) {
            if (body == null) {
                return HookResult.PassResult.instance();
            }
            IEvalScope scope = EvalExprProvider.newEvalScope();
            Object result = body.call2(null, ctx, ctx.getExecutionContext(), scope);
            if (result instanceof HookResult) {
                return (HookResult) result;
            }
            return HookResult.PassResult.instance();
        }
    }
}
