package io.nop.auth.sso.login;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.auth.sso.SsoConfig;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.http.api.client.HttpRequest;
import io.nop.ioc.loader.BeanContainerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C4-007（plan 2508 裁定①）rule-23 接线验证（M1）：超时键
 * {@code nop.auth.sso.refresh-token-timeout} 必须经 sso-defaults.beans.xml 的
 * nopSsoConfig bean 逐属性 {@code @cfg:} 引用真实注入 {@link SsoConfig}——
 * 防死配置键空心面。
 *
 * <p>装配链 = 真实生产 bean 定义（非测试自证）：http-api/http-client-jdk 提供
 * IHttpClient，auth-core-defaults 提供 IUserContextCache，sso-defaults 提供
 * nopLoginService（OAuthLoginServiceImpl）+ nopSsoConfig。经真实 IoC 容器启动，
 * 断言：(1) 默认分支注入 10000；(2) AppConfig 覆盖后经容器启动的 bean 注入覆盖值；
 * (3) nopLoginService 装配的 OAuthLoginServiceImpl 产出的刷新请求携带该值
 * （≤0 档回落：不设显式请求超时）。
 */
public class TestSsoRefreshTokenTimeoutWiring {

    private static final String CFG_ENABLED = "nop.auth.sso.enabled";
    private static final String CFG_TIMEOUT = "nop.auth.sso.refresh-token-timeout";
    // 无行内默认的强制键（@cfg: 无 | 默认 = mandatory）：容器启动前必须赋值
    private static final String CFG_SERVER_URL = "nop.auth.sso.server-url";
    private static final String CFG_REALM = "nop.auth.sso.realm";
    private static final String CFG_CLIENT_ID = "nop.auth.sso.client-id";

    private static IBeanContainer container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        // 条件装配开关（sso-defaults.beans.xml 三个 bean 均以 if-property 为条件）
        AppConfig.getConfigProvider().assignConfigValue(CFG_ENABLED, true);
        AppConfig.getConfigProvider().assignConfigValue(CFG_SERVER_URL, "https://idp.example.com");
        AppConfig.getConfigProvider().assignConfigValue(CFG_REALM, "master");
        AppConfig.getConfigProvider().assignConfigValue(CFG_CLIENT_ID, "test-client");
        container = newContainer();
        container.start();
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        // 清理全局配置覆盖，避免泄漏到同 JVM 其他测试
        AppConfig.getConfigProvider().assignConfigValue(CFG_ENABLED, null);
        AppConfig.getConfigProvider().assignConfigValue(CFG_TIMEOUT, null);
        AppConfig.getConfigProvider().assignConfigValue(CFG_SERVER_URL, null);
        AppConfig.getConfigProvider().assignConfigValue(CFG_REALM, null);
        AppConfig.getConfigProvider().assignConfigValue(CFG_CLIENT_ID, null);
        CoreInitialization.destroy();
    }

    private static IBeanContainer newContainer() {
        return new BeanContainerBuilder((IBeanContainer) null)
                .addResource(vfs("/nop/http/beans/http-api.beans.xml"))
                .addResource(vfs("/nop/http/beans/http-client-jdk.beans.xml"))
                .addResource(vfs("/nop/auth/beans/auth-core-defaults.beans.xml"))
                .addResource(vfs("/nop/auth/beans/sso-defaults.beans.xml"))
                .build("test-sso-wiring");
    }

    private static IResource vfs(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        assertTrue(resource.exists(), "beans resource must exist on test classpath: " + path);
        return resource;
    }

    /** 默认分支：未配置覆盖时 nopSsoConfig 注入字段默认 10000（与 @cfg: 行内默认一致）。 */
    @Test
    public void defaultRefreshTokenTimeoutWiredThroughBeansXml() {
        SsoConfig config = (SsoConfig) container.getBean("nopSsoConfig");
        assertNotNull(config, "nopSsoConfig bean must be registered when nop.auth.sso.enabled=true");

        assertEquals(10000, config.getRefreshTokenTimeout(),
                "default @cfg: inline default (10000) must match the SsoConfig field default");

        OAuthLoginServiceImpl loginService = (OAuthLoginServiceImpl) container.getBean("nopLoginService");
        assertNotNull(loginService, "nopLoginService bean must be wired to the real OAuthLoginServiceImpl");
        HttpRequest req = loginService.newRefreshTokenRequest("refresh-token");
        assertEquals(10000L, req.getTimeout(),
                "the assembled OAuthLoginServiceImpl must produce refresh requests carrying the wired timeout");
    }

    /** rule-23：AppConfig 配置覆盖真实生效——容器启动时经 @cfg: 行注入覆盖值到 bean。 */
    @Test
    public void configOverrideReachesBeanThroughCfgWiring() {
        IBeanContainer overrideContainer = openOverrideContainer(7654);
        try {
            SsoConfig config = (SsoConfig) overrideContainer.getBean("nopSsoConfig");
            assertEquals(7654, config.getRefreshTokenTimeout(),
                    "config override must reach the SsoConfig bean through the @cfg: property line");

            OAuthLoginServiceImpl loginService = (OAuthLoginServiceImpl) overrideContainer.getBean("nopLoginService");
            HttpRequest req = loginService.newRefreshTokenRequest("refresh-token");
            assertEquals(7654L, req.getTimeout(),
                    "captured refresh request timeout must equal the config override value");
        } finally {
            overrideContainer.stop();
        }
    }

    /** ≤0 豁免档：不设显式请求超时（req.timeout 保持 HttpRequest 默认 0），回落客户端部署级默认。 */
    @Test
    public void nonPositiveTimeoutDisablesExplicitRequestTimeout() {
        IBeanContainer overrideContainer = openOverrideContainer(0);
        try {
            SsoConfig config = (SsoConfig) overrideContainer.getBean("nopSsoConfig");
            assertEquals(0, config.getRefreshTokenTimeout());

            OAuthLoginServiceImpl loginService = (OAuthLoginServiceImpl) overrideContainer.getBean("nopLoginService");
            HttpRequest req = loginService.newRefreshTokenRequest("refresh-token");
            assertEquals(0L, req.getTimeout(),
                    "timeout<=0 must not set an explicit request timeout (client-level default fallback)");
        } finally {
            overrideContainer.stop();
        }
    }

    private IBeanContainer openOverrideContainer(int timeoutValue) {
        AppConfig.getConfigProvider().assignConfigValue(CFG_TIMEOUT, timeoutValue);
        IBeanContainer overrideContainer = newContainer();
        overrideContainer.start();
        return overrideContainer;
    }
}
