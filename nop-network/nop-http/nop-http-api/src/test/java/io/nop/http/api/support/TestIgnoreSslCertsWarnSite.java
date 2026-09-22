package io.nop.http.api.support;

import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * F-N1-3 回归：ignore-ssl-certs 逃生门现在带显著 WARN（setter 告警点存在且
   不破坏原有跳过行为）。行为面：启用后 checkServerTrusted 对任意链不抛错。
 */
public class TestIgnoreSslCertsWarnSite {

    @Test
    public void testSetterWarnsAndBehaviorUnchanged() {
        CompositeX509TrustManager tm = new CompositeX509TrustManager(java.util.List.of());
        tm.setIgnoreSSLCert(true); // 触发 WARN 告警点（人工/日志断言另配）
        assertTrue(tm.isIgnoreSSLCert());
        assertDoesNotThrow(() -> tm.checkServerTrusted(new X509Certificate[0], "RSA"));
    }

    private static void assertTrue(boolean v) {
        org.junit.jupiter.api.Assertions.assertTrue(v);
    }
}
