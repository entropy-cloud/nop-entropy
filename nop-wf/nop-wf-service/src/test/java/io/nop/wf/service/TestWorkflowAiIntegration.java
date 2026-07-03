package io.nop.wf.service;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.engine.WorkflowEngineImpl;
import io.nop.wf.core.impl.WorkflowManagerImpl;
import io.nop.wf.service.mock.MockWfActorResolver;
import io.nop.wf.service.mock.MockWorkflowStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestWorkflowAiIntegration extends BaseTestCase {
    WorkflowManagerImpl workflowManager;
    private IBeanContainer originalBeanContainer;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        workflowManager = new WorkflowManagerImpl();

        WorkflowEngineImpl engine = new WorkflowEngineImpl();
        engine.setWfActorResolver(new MockWfActorResolver());
        workflowManager.setWorkflowEngine(engine);
        workflowManager.setWorkflowStore(new MockWorkflowStore());
        workflowManager.init();

        if (BeanContainer.isInitialized()) {
            originalBeanContainer = BeanContainer.instance();
        } else {
            originalBeanContainer = null;
        }
    }

    @AfterEach
    void tearDown() {
        if (originalBeanContainer != null) {
            BeanContainer.registerInstance(originalBeanContainer);
        }
    }

    @Test
    public void testAiDecideWorkflowTransitionsToEnd() {
        RecordingChatService chatService = new RecordingChatService("{\"decision\":\"PASS\",\"confidence\":0.95,\"variables\":{\"riskLevel\":\"low\"}}");
        overrideChatService(chatService);

        IServiceContext context = new ServiceContextImpl();
        context.getContext().setUserId("1");

        IWorkflow workflow = workflowManager.newWorkflow("test/ai-decide", 1L);
        workflow.start(null, context);
        workflow.runAutoTransitions(context);

        assertTrue(workflow.isEnded());
        assertEquals("low", workflow.getGlobalVars().getVar("riskLevel"));
        assertEquals(1, chatService.callCount.get());
    }

    @Test
    public void testAiRouteWorkflowCachesDecisionAndChoosesApprovedBranch() {
        RecordingChatService chatService = new RecordingChatService("{\"decision\":\"PASS\",\"route\":\"fast\"}");
        overrideChatService(chatService);

        IServiceContext context = new ServiceContextImpl();
        context.getContext().setUserId("1");

        IWorkflow workflow = workflowManager.newWorkflow("test/ai-route", 1L);
        workflow.start(null, context);
        workflow.runAutoTransitions(context);
        workflow.runAutoTransitions(context);

        assertTrue(workflow.isEnded());
        assertEquals("PASS", workflow.getGlobalVars().getVar("__aiRouteDecision"));
        assertNotNull(workflow.getGlobalVars().getVar("__aiRouteResult"));
        IWorkflowStep approved = workflow.getLatestStepByName("approved");
        assertNotNull(approved);
        assertEquals(1, chatService.callCount.get());
    }

    private void overrideChatService(IChatService chatService) {
        IBeanContainer parent = originalBeanContainer;
        BeanContainer.registerInstance(new OverlayBeanContainer(parent, chatService));
    }

    private static final class RecordingChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger();
        private final String responseBody;

        private RecordingChatService(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return ChatResponse.success(new ChatAssistantMessage(responseBody));
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static final class OverlayBeanContainer implements IBeanContainer {
        private final IBeanContainer parent;
        private final IChatService chatService;

        private OverlayBeanContainer(IBeanContainer parent, IChatService chatService) {
            this.parent = parent;
            this.chatService = chatService;
        }

        @Override
        public String getId() {
            return parent == null ? "test-overlay" : parent.getId();
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void restart() {
        }

        @Override
        public boolean containsBean(String name) {
            return parent != null && parent.containsBean(name);
        }

        @Override
        public boolean isRunning() {
            return parent == null || parent.isRunning();
        }

        @Override
        public Object getBean(String name) {
            return parent == null ? null : parent.getBean(name);
        }

        @Override
        public boolean containsBeanType(Class<?> clazz) {
            return clazz == IChatService.class || parent != null && parent.containsBeanType(clazz);
        }

        @Override
        public <T> T getBeanByType(Class<T> clazz) {
            T bean = tryGetBeanByType(clazz);
            if (bean == null) {
                throw new IllegalArgumentException("invalid bean type: " + clazz);
            }
            return bean;
        }

        @Override
        public <T> T tryGetBeanByType(Class<T> clazz) {
            if (clazz.isInstance(chatService)) {
                return clazz.cast(chatService);
            }
            return parent == null ? null : parent.tryGetBeanByType(clazz);
        }

        @Override
        public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
            if (clazz.isInstance(chatService)) {
                Map<String, T> beans = parent == null ? new java.util.HashMap<>() : parent.getBeansOfType(clazz);
                beans.put("testChatService", clazz.cast(chatService));
                return beans;
            }
            return parent == null ? new java.util.HashMap<>() : parent.getBeansOfType(clazz);
        }

        @Override
        public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
            return parent == null ? new java.util.HashMap<>() : parent.getBeansWithAnnotation(annClass);
        }

        @Override
        public String getBeanScope(String name) {
            return parent == null ? "singleton" : parent.getBeanScope(name);
        }

        @Override
        public Class<?> getBeanClass(String name) {
            return parent == null ? null : parent.getBeanClass(name);
        }

        @Override
        public String findAutowireCandidate(Class<?> beanType) {
            if (beanType == IChatService.class) {
                return "testChatService";
            }
            return parent == null ? null : parent.findAutowireCandidate(beanType);
        }

        @Override
        public boolean supportInjectTo() {
            return parent != null && parent.supportInjectTo();
        }

        @Override
        public void injectTo(Object bean) {
            if (parent != null) {
                parent.injectTo(bean);
            }
        }
    }
}
