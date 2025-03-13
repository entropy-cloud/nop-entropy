package io.nop.task.impl;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.step.AbstractTaskStep;
import io.nop.xlang.xdsl.DslModelHelper;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestTaskFlowManager extends AbstractTaskTestCase {
    @Test
    public void testXpl01() {
        runTask("test/xpl-01");
    }

    @Test
    public void testSequential01() {
        runTask("test/sequential-01");
    }

    @Test
    public void testLoop01() {
        runTask("test/loop-01");
    }


    @Test
    public void testLoopN01() {
        runTask("test/loop-n-01");
    }

    @Test
    public void testChoose01() {
        runTask("test/choose-01");
    }

    @Test
    public void testExit01() {
        runTask("test/exit-01");
    }

    @Test
    public void testSleep01() {
        runTask("test/sleep-01");
    }

    @Test
    public void testDelay01() {
        runTask("test/delay-01");
    }

    @Test
    public void testCallTask01() {
        Map<String, Object> ret = runTask("test/call-task-01");
        assertFalse(ret.containsKey("c"));
        assertEquals(2, ret.get("b"));
    }

    @Test
    public void testCallStep01() {
        runTask("test/call-step-01");
    }

    @Test
    public void testInvoke01() {
        BeanContainer.registerInstance(new MockBeanContainer());
        runTask("test/invoke-01");
    }

    @Test
    public void testInvokeStatic() {
        BeanContainer.registerInstance(new MockBeanContainer());
        runTask("test/invoke-static");
    }


    @Test
    public void testSimple01() {
        BeanContainer.registerInstance(new MockBeanContainer());
        runTask("test/simple-01");
    }

    @Test
    public void testParallel01() {
        BeanContainer.registerInstance(new MockBeanContainer());
        runTask("test/parallel-01");
    }

    @Test
    public void testFork01() {
        runTask("test/fork-01");
    }

    @Test
    public void testForkN01() {
        runTask("test/fork-n-01");
    }

    @Test
    public void testGraph01() {
        BeanContainer.registerInstance(new MockBeanContainer());
        runTask("test/graph-01");
    }


    @Test
    public void testRunOnContext() {
        BeanContainer.registerInstance(new MockBeanContainer());
        ContextProvider.getOrCreateContext().runOnContext(() -> {
            runTask("test/run-on-context-01");
        });
    }

    @Test
    public void testJsonFormat() {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/task/test/sequential-01/v1.task.xml");
        DynamicObject bean = DslModelHelper.loadDslModelAsJson(resource, true);
        System.out.println(JsonTool.serializeToYaml(bean));

        XNode node = DslModelHelper.dslModelToXNode("/nop/schema/task/task.xdef", bean);
        node.dump();
        assertEquals(attachmentXml("test-json.task.xml").xml(), node.xml());
    }

    @Test
    public void testJsonFormat01() {
        runTask("test/json-format-01");
    }

    @Test
    public void testCustomType() {
        runTask("test/custom-type-01");
    }

    @Test
    public void testTransform() {
        runTask("test/transform");
    }

    public static class MyHandler {

        public int myMethod(int x) {
            return x + 1;
        }
    }

    static class MyStep extends AbstractTaskStep {
        @Nonnull
        @Override
        public TaskStepReturn execute(ITaskStepRuntime stepRt) {
            return TaskStepReturn.of(null, 3);
        }
    }

    static class MockBeanContainer implements IBeanContainer {
        private final Map<String, Object> beans = new HashMap<>();

        public MockBeanContainer() {
            beans.put("myExecutor", GlobalExecutors.globalWorker());
            beans.put("myHandler", new MyHandler());
            beans.put("myStep", new MyStep());
        }

        @Override
        public String getId() {
            return null;
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
            return beans.containsKey(name);
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Nonnull
        @Override
        public Object getBean(String name) {
            return beans.get(name);
        }

        @Override
        public boolean containsBeanType(Class<?> clazz) {
            return false;
        }

        @Nonnull
        @Override
        public <T> T getBeanByType(Class<T> clazz) {
            return null;
        }

        @Override
        public <T> T tryGetBeanByType(Class<T> clazz) {
            return null;
        }

        @Override
        public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
            return null;
        }

        @Override
        public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
            return null;
        }

        @Override
        public String getBeanScope(String name) {
            return null;
        }

        @Override
        public Class<?> getBeanClass(String name) {
            return null;
        }

        @Override
        public String findAutowireCandidate(Class<?> beanType) {
            return null;
        }
    }
}