package io.nop.ai.core.prompt;

import io.nop.ai.core.model.PromptModel;
import io.nop.ai.core.model.PromptOutputModel;
import io.nop.ai.core.response.CodeResponseParser;
import io.nop.ai.core.response.JsonResponseParser;
import io.nop.ai.core.response.MarkdownResponseParser;
import io.nop.ai.core.response.XmlResponseParser;
import io.nop.ai.core.response.YamlResponseParser;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-MA-13（plan 2555 裁定 C / C1-001+C1-002）护栏载体：
 * 1. 并发首用 PromptTemplateManager.loadPromptTemplateFromPath —— 所有并发线程
 * 必须拿到同一 IPromptTemplate 实例（per-model 锁内双检记忆化，至多构造一次）；
 * 2. volatile 结构钉：PromptModel.name/promptTemplate、PromptOutputModel 四字段、
 * MA-12（裁定 B / C1-003）七个 static 单例持有者字段。
 */
@NopTestConfig
public class TestPromptMemoizationConcurrency extends JunitBaseTestCase {

    @Inject
    IPromptTemplateManager promptTemplateManager;

    private static String anyPromptPath() {
        Collection<? extends IResource> resources =
                VirtualFileSystem.instance().getAllResources("/nop/ai/prompts", "prompt.yaml");
        for (IResource r : resources) {
            if (r.getName().endsWith(".prompt.yaml"))
                return r.getPath();
        }
        return null;
    }

    @Test
    public void testConcurrentFirstUseYieldsSingleTemplateInstance() throws Exception {
        String path = anyPromptPath();
        assertNotNull(path, "no .prompt.yaml resources found under /nop/ai/prompts");

        int threads = 8;
        // 预热装载组件模型本身（loadComponentModel 的装载锁不属本钉面），
        // 并清除可能已存在的记忆化模板，保证测的是"并发首用"路径
        PromptModel model = (PromptModel) io.nop.core.resource.component.ResourceComponentManager
                .instance().loadComponentModel(path);
        model.setPromptTemplate(null);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<IPromptTemplate>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<IPromptTemplate> task = () -> {
                    start.await();
                    return promptTemplateManager.loadPromptTemplateFromPath(path);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            Set<IPromptTemplate> distinct = new CopyOnWriteArraySet<>();
            for (Future<IPromptTemplate> f : futures) {
                IPromptTemplate tpl = f.get(30, TimeUnit.SECONDS);
                assertNotNull(tpl);
                distinct.add(tpl);
            }
            assertEquals(1, distinct.size(),
                    "concurrent first-use must memoize exactly one template instance");
            assertEquals(System.identityHashCode(futures.get(0).get()),
                    System.identityHashCode(model.getPromptTemplate()),
                    "memoized template must be published on the model");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testVolatileStructurePins() throws Exception {
        // MA-13（裁定 C）
        assertVolatileInstanceField(PromptModel.class, "promptTemplate");
        assertVolatileInstanceField(PromptModel.class, "name");
        assertVolatileInstanceField(PromptOutputModel.class, "xdefObj");
        assertVolatileInstanceField(PromptOutputModel.class, "xdefForAi");
        assertVolatileInstanceField(PromptOutputModel.class, "markdownTpl");
        assertVolatileInstanceField(PromptOutputModel.class, "markdownTplWithoutDetail");

        // MA-12（裁定 B / C1-003 七 static 单例）
        assertVolatileStaticField(XmlResponseParser.class, "s_instance");
        assertVolatileStaticField(MarkdownResponseParser.class, "s_instance");
        assertVolatileStaticField(CodeResponseParser.class, "s_instance");
        assertVolatileStaticField(JsonResponseParser.class, "s_instance");
        assertVolatileStaticField(YamlResponseParser.class, "_instance");
        assertVolatileStaticField(DefaultSystemPromptLoader.class, "_instance");
        assertVolatileStaticField(
                Class.forName("io.nop.ai.core.initialize.PromptSyntaxStdDomainHandler"), "INSTANCE");
    }

    private static void assertVolatileInstanceField(Class<?> clazz, String name) throws Exception {
        Field f = clazz.getDeclaredField(name);
        assertTrue(Modifier.isVolatile(f.getModifiers()),
                clazz.getSimpleName() + "." + name + " must be volatile (plan 2555 裁定 C)");
    }

    private static void assertVolatileStaticField(Class<?> clazz, String name) throws Exception {
        Field f = clazz.getDeclaredField(name);
        assertTrue(Modifier.isStatic(f.getModifiers()) && Modifier.isVolatile(f.getModifiers()),
                clazz.getName() + "." + name + " must be a volatile static field (plan 2555 裁定 B/C1-003)");
    }
}
