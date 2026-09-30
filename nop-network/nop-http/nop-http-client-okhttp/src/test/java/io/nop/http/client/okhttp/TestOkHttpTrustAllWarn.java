package io.nop.http.client.okhttp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.http.api.client.HttpClientConfig;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G12-13-01 回归：{@code useSsl=true} 且未注入显式 TrustManager/HostnameVerifier 时，
 * OkHttp 客户端默认安装 {@link OkHttpClientProvider.DisableValidationTrustManager}
 * （空实现）+ {@link OkHttpClientProvider.TrustAllHostnames}（恒 true），即证书链与
 * 主机名校验全部关闭。
 *
 * <p>修复后的行为契约（plan 2283 WS3）：
 * <ul>
 *   <li><b>默认行为不变</b>（Deferred 裁定）：trust-all 组件仍被安装，兼容现状；</li>
 *   <li><b>不再静默</b>：两个放行组件各自的安装点输出显著 WARN（对齐
 *       {@code CompositeX509TrustManager} 的 F-N1-3 告警基线），消息说明 MITM 风险与
 *       显式注入替代方案（{@code setTrustManager}/{@code setHostnameVerifier}）。</li>
 * </ul>
 *
 * <p>WARN 断言使用 logback {@link ListAppender} 直连
 * {@code OkHttpClientProvider} 的 SLF4J Logger（与 nop-dao {@code TestSqlLogSwitch} 同款手段）。
 */
public class TestOkHttpTrustAllWarn {

    private static final String TRUST_MANAGER_WARN_MARK = "nop.http.okhttp.trust-all-trust-manager-enabled";
    private static final String HOSTNAME_VERIFIER_WARN_MARK = "nop.http.okhttp.trust-all-hostname-verifier-enabled";

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(OkHttpClientProvider.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private List<String> warnMessages() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    static OkHttpClientProvider newProvider(boolean useSsl) {
        OkHttpClientProvider provider = new OkHttpClientProvider();
        HttpClientConfig config = new HttpClientConfig();
        config.setUseSsl(useSsl);
        provider.setConfig(config);
        return provider;
    }

    @Test
    public void trustAllDefaultEmitsWarnAndKeepsCompatBehaviour() {
        OkHttpClientProvider provider = newProvider(true);

        // 触发 SSL 装配路径（默认 trustManager / hostnameVerifier 均未注入）
        OkHttpClient client = provider.newBuilder().build();

        // 默认行为不变（Deferred 裁定）：trust-all hostname 校验组件仍被安装
        assertSame(OkHttpClientProvider.TrustAllHostnames.class, client.hostnameVerifier().getClass(),
                "default behaviour must stay trust-all (compat adjudication)");
        // 证书链放行组件被真实安装：sslSocketFactory 非 null 即完成装配（build 未抛错）
        assertTrue(client.sslSocketFactory() != null, "ssl socket factory must be installed");

        // 新行为：两个放行组件的安装点各输出一条 WARN，说明 MITM 风险与显式注入替代方案
        List<String> warns = warnMessages();
        assertTrue(warns.stream().anyMatch(m -> m.contains(TRUST_MANAGER_WARN_MARK)),
                "installing the default DisableValidationTrustManager must emit a WARN; got: " + warns);
        assertTrue(warns.stream().anyMatch(m -> m.contains(HOSTNAME_VERIFIER_WARN_MARK)),
                "installing the default TrustAllHostnames must emit a WARN; got: " + warns);
        assertTrue(warns.stream().anyMatch(m -> m.contains("man-in-the-middle")),
                "the WARN must explain the MITM risk; got: " + warns);
        assertTrue(warns.stream().anyMatch(m -> m.contains("setTrustManager")),
                "the WARN must point at the explicit injection alternative; got: " + warns);
    }

    @Test
    public void explicitlyInjectedComponentsDoNotTriggerDefaultWarns() {
        OkHttpClientProvider provider = newProvider(true);
        // 显式注入（即使注入的仍是放行实现——那是调用者的显式选择，不再是"静默默认"）
        provider.setTrustManager(new OkHttpClientProvider.DisableValidationTrustManager());
        provider.setHostnameVerifier(new OkHttpClientProvider.TrustAllHostnames());

        provider.newBuilder().build();

        List<String> warns = warnMessages();
        assertEquals(0, warns.stream().filter(m -> m.contains(TRUST_MANAGER_WARN_MARK)).count(),
                "an explicitly injected trust manager must not trigger the default-install WARN; got: " + warns);
        assertEquals(0, warns.stream().filter(m -> m.contains(HOSTNAME_VERIFIER_WARN_MARK)).count(),
                "an explicitly injected hostname verifier must not trigger the default-install WARN; got: " + warns);
    }

    @Test
    public void useSslOffDoesNotWarn() {
        OkHttpClientProvider provider = newProvider(false);
        provider.newBuilder().build();
        assertTrue(warnMessages().isEmpty(),
                "useSsl=false must not emit any trust-all WARN; got: " + warnMessages());
    }
}
