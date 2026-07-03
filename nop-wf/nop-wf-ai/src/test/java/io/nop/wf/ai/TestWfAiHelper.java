package io.nop.wf.ai;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.unittest.BaseTestCase;
import io.nop.core.utils.MapVarSet;
import io.nop.wf.core.impl.IWorkflowImplementor;
import io.nop.wf.core.impl.IWorkflowStepImplementor;
import io.nop.wf.core.engine.IWfRuntime;
import io.nop.wf.core.store.beans.WorkflowRecordBean;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestWfAiHelper extends BaseTestCase {
    @AfterEach
    void cleanupBeanContainer() {
        BeanContainer.registerProvider(null);
    }

    @Test
    public void testDecideMarksAgreeAndWritesVariables() {
        RecordingChatService chatService = new RecordingChatService("{\"decision\":\"PASS\",\"confidence\":0.95,\"variables\":{\"riskLevel\":\"low\"}}");
        registerChatService(chatService);

        WorkflowRecordBean wfRecord = new WorkflowRecordBean();
        wfRecord.setStatus(1);
        WorkflowStepRecordBean stepRecord = new WorkflowStepRecordBean();

        RuntimeFixture fixture = new RuntimeFixture(wfRecord, stepRecord);
        Map<String, Object> result = WfAiHelper.decide("approve?", 0.8D, null, null, fixture.runtime);

        assertEquals("PASS", result.get("decision"));
        assertEquals("agree", stepRecord.getAppState());
        assertEquals("low", fixture.globalVars.getVar("riskLevel"));
        assertEquals(1, chatService.callCount.get());
        assertEquals("json", chatService.lastRequest.get().getOptions().getResponseFormat());
    }

    @Test
    public void testDecideLowConfidenceMovesToManualReview() {
        RecordingChatService chatService = new RecordingChatService("{\"decision\":\"PASS\",\"confidence\":0.2}");
        registerChatService(chatService);

        WorkflowRecordBean wfRecord = new WorkflowRecordBean();
        WorkflowStepRecordBean stepRecord = new WorkflowStepRecordBean();
        RuntimeFixture fixture = new RuntimeFixture(wfRecord, stepRecord);

        Map<String, Object> result = WfAiHelper.decide("approve?", 0.8D, "manual", null, fixture.runtime);

        assertEquals("PASS", result.get("decision"));
        assertEquals("manual-review", fixture.changedOwnerId.get());
        assertNull(stepRecord.getAppState());
    }

    @Test
    public void testDecideSuspendsWorkflowOnError() {
        RecordingChatService chatService = new RecordingChatService(new IllegalStateException("boom"));
        registerChatService(chatService);

        WorkflowRecordBean wfRecord = new WorkflowRecordBean();
        WorkflowStepRecordBean stepRecord = new WorkflowStepRecordBean();
        RuntimeFixture fixture = new RuntimeFixture(wfRecord, stepRecord);

        Map<String, Object> result = WfAiHelper.decide("approve?", 0.8D, null, "suspend", fixture.runtime);

        assertNull(result);
        assertTrue(fixture.suspended.get());
        assertFalse(fixture.changedOwnerId.get() != null);
    }

    @Test
    public void testRouteCachesFirstResultAndJudgeReadsDecision() {
        RecordingChatService chatService = new RecordingChatService("{\"decision\":\"PASS\",\"route\":\"fast\"}");
        registerChatService(chatService);

        WorkflowRecordBean wfRecord = new WorkflowRecordBean();
        WorkflowStepRecordBean stepRecord = new WorkflowStepRecordBean();
        RuntimeFixture fixture = new RuntimeFixture(wfRecord, stepRecord);

        Map<String, Object> first = WfAiHelper.route("route", fixture.runtime);
        boolean passed = WfAiHelper.judge("route again", fixture.runtime);
        Map<String, Object> cached = WfAiHelper.extract("extract", fixture.runtime);

        assertSame(first, cached);
        assertTrue(passed);
        assertEquals("PASS", fixture.globalVars.getVar("__aiRouteDecision"));
        assertNotNull(fixture.globalVars.getVar("__aiRouteResult"));
        assertEquals(1, chatService.callCount.get());
    }

    private void registerChatService(IChatService chatService) {
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("chatService", chatService);
        BeanContainer.registerInstance(container);
    }

    private static final class RecordingChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger();
        final AtomicReference<ChatRequest> lastRequest = new AtomicReference<>();
        private final String responseBody;
        private final RuntimeException failure;

        private RecordingChatService(String responseBody) {
            this.responseBody = responseBody;
            this.failure = null;
        }

        private RecordingChatService(RuntimeException failure) {
            this.responseBody = null;
            this.failure = failure;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            lastRequest.set(request);
            if (failure != null) {
                throw failure;
            }
            return ChatResponse.success(new ChatAssistantMessage(responseBody));
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static final class RuntimeFixture {
        final MapVarSet globalVars = new MapVarSet();
        final AtomicReference<String> changedOwnerId = new AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean suspended = new java.util.concurrent.atomic.AtomicBoolean();
        final IWfRuntime runtime;

        RuntimeFixture(WorkflowRecordBean workflowRecord, WorkflowStepRecordBean stepRecord) {
            IWorkflowImplementor workflow = proxy(IWorkflowImplementor.class, (proxy, method, args) -> {
                String name = method.getName();
                if ("getGlobalVars".equals(name)) {
                    return globalVars;
                }
                if ("getRecord".equals(name)) {
                    return workflowRecord;
                }
                if ("suspend".equals(name)) {
                    suspended.set(true);
                    return null;
                }
                return defaultValue(method.getReturnType());
            });

            IServiceContext svcCtx = new ServiceContextImpl();
            IWorkflowStepImplementor step = proxy(IWorkflowStepImplementor.class, (proxy, method, args) -> {
                String name = method.getName();
                if ("changeOwnerId".equals(name)) {
                    changedOwnerId.set((String) args[0]);
                    return null;
                }
                if ("getRecord".equals(name)) {
                    return stepRecord;
                }
                if ("getWorkflow".equals(name)) {
                    return workflow;
                }
                return defaultValue(method.getReturnType());
            });

            runtime = proxy(IWfRuntime.class, (proxy, method, args) -> {
                String name = method.getName();
                if ("getCurrentStep".equals(name)) {
                    return step;
                }
                if ("getWf".equals(name)) {
                    return workflow;
                }
                if ("getSvcCtx".equals(name)) {
                    return svcCtx;
                }
                return defaultValue(method.getReturnType());
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == null || returnType == Void.TYPE) {
            return null;
        }
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        return null;
    }
}
