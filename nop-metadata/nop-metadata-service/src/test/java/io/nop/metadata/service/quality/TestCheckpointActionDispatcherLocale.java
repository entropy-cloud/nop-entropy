package io.nop.metadata.service.quality;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * INV-LOCALE（Cycle 2 / P1-B，adjudication-table-cycle2 §2 #32-#35）：webhook URL 安全校验路径的
 * tr-TR 默认 locale 回归。
 *
 * <p>修复前 allowed-webhook-hosts 集合归一化与 URL 侧 host 小写化使用默认 locale {@code toLowerCase()}：
 * tr-TR 下 host 含大写 {@code I}（如 {@code Api.Intra.Localhost}）小写化为 {@code ı}——与放行清单
 * （普通 {@code i}）失配 → 已放行的内网 webhook 主机被误拒（loud but wrong）。修复后机器比较语义
 * 统一 {@code Locale.ROOT}。
 */
public class TestCheckpointActionDispatcherLocale {

    // validateWebhookUrl 不触碰 httpClient/messageService，构造传 null 即可
    private final CheckpointActionDispatcher dispatcher = new CheckpointActionDispatcher(null, null);

    private static NopMetaQualityCheckpoint cp() {
        NopMetaQualityCheckpoint cp = new NopMetaQualityCheckpoint();
        cp.setCheckpointId("cp-locale-test");
        return cp;
    }

    /** tr-TR 默认 locale 下，allowed-hosts 放行的混合大小写内网 webhook host 必须仍匹配放行。 */
    @Test
    public void turkishLocaleAllowedWebhookHostsStillMatchMixedCaseUrlHost() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            dispatcher.configureWebhookSsrf("api.intra.localhost", 5);
            assertDoesNotThrow(() -> dispatcher.validateWebhookUrl(cp(),
                            "https://Api.Intra.Localhost:8443/hook"),
                    "configured allowed internal webhook host must match mixed-case URL host under tr-TR");
            // 未配置放行时内网 host 仍拒绝（fail-closed 基线不受修复影响）
            dispatcher.configureWebhookSsrf("", 5);
            assertThrows(NopException.class,
                    () -> dispatcher.validateWebhookUrl(cp(), "https://Api.Intra.Localhost:8443/hook"),
                    "internal webhook host without allowance must stay blocked under tr-TR");
        } finally {
            Locale.setDefault(original);
        }
    }

    /** 默认 locale 下的既有 webhook 基线（回归，不依赖 tr）。 */
    @Test
    public void defaultLocaleWebhookHostsBaseline() {
        assertDoesNotThrow(() -> dispatcher.validateWebhookUrl(cp(), "https://public.example.com/hook"),
                "public webhook host must pass");
        assertThrows(NopException.class,
                () -> dispatcher.validateWebhookUrl(cp(), "ftp://public.example.com/hook"),
                "non-http protocol must be rejected");
    }
}
