package io.nop.ai.core.service;

import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * K1（plan knowledge-rag/01）Phase 3 bean 装配冒烟：经标准 IoC 装配链加载
 * {@code /nop/ai/beans/ai-defaults.beans.xml}（即 autoconfig
 * {@code nop-ai-core.beans} 指向的生产 beans 文件）后，{@code IEmbeddingModel}
 * 按类型可解析为容器管理的 {@code EmbeddingServiceImpl} 实例（Rule #23 接线验证：
 * bean 定义真实进入运行时容器并被实例化，非文本存在）。
 *
 * <p>经 {@code nop.ioc.app-beans.files} 限定容器只装配本验证所需的最小 beans 集
 * （http client + ai-defaults），避免 nop-ai-core 测试类路径引入完整应用栈
 * （datasource 等）的无关装配要求。
 */
class TestEmbeddingModelBeanAssembly {

    @BeforeAll
    static void init() {
        // 只装配本验证所需的最小 beans 集：关闭其余三条装载路径（merged/autoconfig/app-beans-file 扫描），
        // 经 app-beans.files 显式指定 http client + ai-defaults（nop-ai-core 测试类路径无 datasource 配置，
        // 全量 autoconfig 会拉起 dao beans 而失败）。
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.merged-beans-file.enabled", "false");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.auto-config.enabled", "false");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.enabled", "false");
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans.files",
                "/nop/http/beans/http-api.beans.xml,"
                        + "/nop/http/beans/http-client-jdk.beans.xml,"
                        + "/nop/ai/beans/ai-defaults.beans.xml");
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.merged-beans-file.enabled", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.auto-config.enabled", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans-file.enabled", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ioc.app-beans.files", null);
    }

    @Test
    void embeddingModelResolvableByTypeFromAutoConfig() {
        IBeanContainer container = BeanContainer.instance();
        assertTrue(container.isRunning(), "app container must be running");

        Object byId = container.getBean("nopAiEmbeddingModel");
        assertNotNull(byId, "nopAiEmbeddingModel must be assembled from ai-defaults.beans.xml");
        assertTrue(byId instanceof EmbeddingServiceImpl,
                "bean must be EmbeddingServiceImpl, got: " + byId.getClass());

        IEmbeddingModel byType = container.getBeanByType(IEmbeddingModel.class);
        assertNotNull(byType, "IEmbeddingModel must be resolvable by type");
        assertTrue(byType instanceof EmbeddingServiceImpl,
                "type-resolved bean must be EmbeddingServiceImpl, got: " + byType.getClass());
    }
}
