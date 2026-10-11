package io.nop.ai.core.search;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.ITextEmbedding;
import io.nop.search.lucene.LuceneSearchEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N4.2（plan nop-code/23）Phase 2 wiring 验证（Rule #23）：经真实 IoC 容器，
 * `LuceneSearchEngine.setTextEmbedding`（@Inject+@Nullable）按类型自动注入
 * `nopAiTextEmbedding`（`AiModelTextEmbedding`），且桥接实例持有 stub 模型——
 * 引擎 ← 桥接 ← 模型整条调用链在容器内接通，非仅类型存在。
 */
class TestTextEmbeddingWiring {

    @BeforeAll
    static void init() {
        AppConfig.getConfigProvider().assignConfigValue("nop.search.index-dir", "./target/n42-wiring");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.merged-beans-file.enabled", "false");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.auto-config.enabled", "false");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.enabled", "false");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans.files",
                "/nop/ai/beans/n42-test-embedding.beans.xml,"
                        + "/nop/search/beans/search-defaults.beans.xml");
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
        AppConfig.getConfigProvider().assignConfigValue("nop.search.index-dir", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.merged-beans-file.enabled", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.auto-config.enabled", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.enabled", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans.files", null);
    }

    @Test
    void engineTextEmbeddingInjectedByType() throws Exception {
        IBeanContainer container = BeanContainer.instance();
        assertTrue(container.isRunning(), "app container must be running");

        LuceneSearchEngine engine = (LuceneSearchEngine) container.getBeanByType(ISearchEngine.class);
        assertNotNull(engine, "nopSearchEngine must be resolvable from search-defaults.beans.xml");

        Object textEmbedding = readField(engine, "textEmbedding");
        assertNotNull(textEmbedding, "engine.textEmbedding must be injected by type (was always null before N4.2)");
        assertTrue(textEmbedding instanceof AiModelTextEmbedding,
                "injected embedding must be the bridge instance, got: " + textEmbedding.getClass());

        Object model = readField(textEmbedding, "embeddingModel");
        assertSame(container.getBean("nopAiEmbeddingModel"), model,
                "bridge must hold the container's IEmbeddingModel instance");
        assertTrue(model instanceof StubEmbeddingModel,
                "wiring container uses the stub model, got: " + model.getClass());
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
