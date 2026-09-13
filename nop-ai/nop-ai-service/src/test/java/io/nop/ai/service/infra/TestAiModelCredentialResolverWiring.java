/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.service.infra;

import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.credential.IAiModelCredentialResolver;
import io.nop.ai.core.service.ChatServiceImpl;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1-audit D6-01 修复回归（2026-08-17）：{@link AiModelCredentialResolverImpl} 的 NopIoC 装配级
 * 接线验证——修复前该实现类从未在任何 beans.xml 注册（NopIoC 无注解扫描），
 * {@code ChatServiceImpl.credentialResolver} 恒为 null，{@code NopAiModel.credentialId}
 * 运行时消费链在所有部署形态下静默失效回退 config apiKey。
 *
 * <p>经完整 app 容器验证（对齐 {@code TestVaultKeyProviderWiring} 先例）：
 * <ul>
 * <li>bean 注册：{@code app-service.beans.xml} 的 {@code nopAiModelCredentialResolver} 条目
 *     经模块自动装载路径生效，按类型与按 id 均解析到实现类；</li>
 * <li>注入接线：nop-ai-core autoconfig 装配的 {@code nopChatService}（{@link ChatServiceImpl}）
 *     的 {@code credentialResolver} 字段持有同一实例（消费链接通）；</li>
 * <li>可选依赖语义：容器内无 nop-credential-service 部署（ICredentialProvider 无 bean）时
 *     启动不失败——解析器保持 fail-closed 调用期语义（ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE）。</li>
 * </ul>
 */
public class TestAiModelCredentialResolverWiring {

    private static IAiModelCredentialResolver resolver;

    @BeforeAll
    public static void setUp() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        BaseTestCase.setTestConfig("nop.ioc.app-beans-container.start-mode", "ALL_LAZY");
        CoreInitialization.initialize();
        resolver = BeanContainer.instance().getBeanByType(IAiModelCredentialResolver.class);
    }

    @AfterAll
    public static void tearDown() {
        CoreInitialization.destroy();
        BaseTestCase.clearTestConfig("nop.ioc.app-beans-container.start-mode");
    }

    @Test
    public void resolverBeanRegisteredInAppContainer() {
        assertNotNull(resolver, "IAiModelCredentialResolver bean must be resolvable from the app container");
        assertTrue(resolver instanceof AiModelCredentialResolverImpl,
                "resolved bean must be AiModelCredentialResolverImpl, got: " + resolver.getClass());
        assertEquals(resolver, BeanContainer.instance().getBean("nopAiModelCredentialResolver"),
                "bean must be registered under id nopAiModelCredentialResolver in app-service.beans.xml");
    }

    @Test
    public void chatServiceInjectedWithResolver() throws Exception {
        IChatService chatService = BeanContainer.instance().getBeanByType(IChatService.class);
        assertNotNull(chatService, "nopChatService must be resolvable (nop-ai-core autoconfig ai-defaults)");
        assertTrue(chatService instanceof ChatServiceImpl,
                "nopChatService must be ChatServiceImpl, got: " + chatService.getClass());

        Field field = ChatServiceImpl.class.getDeclaredField("credentialResolver");
        field.setAccessible(true);
        Object injected = field.get(chatService);
        assertNotNull(injected, "ChatServiceImpl.credentialResolver must be non-null after the D6-01 fix "
                + "(was always null before the bean registration)");
        assertSame(resolver, injected, "ChatServiceImpl must hold the container-registered resolver instance");
    }

    @Test
    public void optionalDependenciesKeepContainerStartableWithoutCredentialModule() {
        // 复核观察项 2（A1-audit successor，2026-08-17 显式断言改写）：原断言
        // assertNotNull(getBean(...)) 仅隐式证明可选装配——bean 存在但构造可能已引入对
        // ICredentialProvider 的隐藏强制解析。改写为显式断言：
        // (a) 本容器（无 nop-credential-service 部署）解析出的 resolver 实例的
        //     credentialProvider 字段为 null——对凭证库 bean 的可选依赖字段确实保持空，
        //     构造路径零强制解析（若存在隐藏依赖，容器启动或本断言即失败）；
        // (b) daoProvider 在本容器有 bean（OrmDaoProvider）故非 null——其可选性的证明
        //     载体是 TestAiModelCredentialResolver 的自建 ORM 栈单测（不经容器注入 dao），
        //     此处不重复断言。
        // 调用期语义（配 credentialId 但 provider 缺失 → ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE）
        // 由 TestAiModelCredentialResolver 覆盖。
        Object bean = BeanContainer.instance().getBean("nopAiModelCredentialResolver");
        assertNotNull(bean, "resolver bean must construct without ICredentialProvider/DB beans (optional wiring)");
        AiModelCredentialResolverImpl impl = (AiModelCredentialResolverImpl) bean;
        assertNull(impl.credentialProvider,
                "resolver construction must not depend on ICredentialProvider (field stays null when the "
                        + "credential lib is absent — explicit optional-wiring assertion, replaces the former "
                        + "implicit assertNotNull proof)");
    }
}
