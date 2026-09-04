/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.ops;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-09 (plan 2026-09-04-1326-3): two trust-boundary proofs for the ops REST surface.
 *
 * <p><b>F-09a class-loading hardening</b>: {@code OpsJobManager} previously ran
 * {@code Class.forName(factoryClass)} (initialize=true) BEFORE the interface check —
 * an arbitrary FQCN from the request body could execute static initializers. Now the
 * name is guarded up front (JDK/internal/array descriptors rejected typed) and the
 * class loads with {@code initialize=false} (interface check first, initialization only
 * at newInstance on a verified factory).
 *
 * <p><b>F-09b minimal auth</b>: a non-loopback bind without a token refuses to start;
 * with a token configured, every endpoint answers 401 for missing/wrong credentials and
 * serves the correct bearer. Loopback default keeps zero-auth (back-compat).
 */
class TestStreamOpsAuthAndClassLoading {

    private StreamOpsHttpServer server;
    private HttpClient client;

    /**
     * Holds the observation flag OUTSIDE the probe class: touching a static field of the
     * probe itself would initialize it (JLS 12.4.1), defeating the check-before-initialize
     * observation. The probe's static initializer only writes this holder.
     */
    public static final class InitFlagHolder {
        public static volatile boolean initialized = false;
    }

    /** Not in any rejected prefix; its static initializer marks the holder. */
    public static final class SideEffectProbe {
        static {
            InitFlagHolder.initialized = true;
        }
    }

    @BeforeEach
    void setUp() {
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    // ====================== F-09a: class-loading hardening ======================

    @Test
    void maliciousFactoryClassNamesAreRejectedTyped() {
        // JDK/internal packages and array descriptors can never be valid
        // ClusterPipelineFactory implementations — rejected by the name guard before
        // any Class.forName call.
        for (String hostile : new String[]{
                "java.lang.Runtime", "javax.naming.InitialContext", "jdk.internal.misc.Unsafe",
                "sun.misc.Service", "com.sun.org.apache.xalan.internal.xslt.Process",
                "[Ljava.lang.String;", "[[Ljava.lang.Object;", "java.lang.ProcessBuilder"}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> OpsJobManager.validateFactoryClassName(hostile),
                    "hostile factory class must be rejected by the name guard: " + hostile);
            assertTrue(String.valueOf(ex).contains("F-09a"),
                    "rejection cites the hardening rule: " + ex);
        }
        assertThrows(IllegalArgumentException.class,
                () -> OpsJobManager.validateFactoryClassName(null));
        assertThrows(IllegalArgumentException.class,
                () -> OpsJobManager.validateFactoryClassName("  "));
    }

    @Test
    void thirdPartyFactoryPrefixesStayAcceptedByTheNameGuard() {
        // pipelineFactoryClass is a documented user-extensible contract (owner doc):
        // a third-party prefix must pass the NAME guard (interface check happens later
        // at load time — this test pins that the guard itself does not over-reject).
        OpsJobManager.validateFactoryClassName("com.mycompany.stream.FraudPipelineFactory");
        OpsJobManager.validateFactoryClassName("org.example.CustomClusterPipeline");
    }

    @Test
    void classLoadingWithoutInitializationRunsNoStaticInitializer() throws Exception {
        // The mechanism proof for check-before-initialize: loading with initialize=false
        // must NOT run the static initializer — the side-effect surface is eliminated
        // before the interface check gates newInstance().
        InitFlagHolder.initialized = false;
        Class<?> clazz = Class.forName(SideEffectProbe.class.getName(), false,
                TestStreamOpsAuthAndClassLoading.class.getClassLoader());
        assertEquals(SideEffectProbe.class, clazz, "class loads without initialization");
        assertFalse(InitFlagHolder.initialized,
                "static initializer must NOT run for Class.forName(name, false, loader)");
        // initialization happens only on first use — mirrors the OpsJobManager path where
        // newInstance() runs AFTER the interface check has already passed.
        clazz.getDeclaredConstructor().newInstance();
        assertTrue(InitFlagHolder.initialized,
                "initializer runs at instantiation (after the interface check in production)");
    }

    // ====================== F-09b: minimal bearer-token gate ======================

    @Test
    void nonLoopbackBindWithoutTokenRefusesToStart() {
        StreamOpsConfig config = new StreamOpsConfig();
        config.setEnabled(true);
        config.setPort(0);
        config.setBindAddress("0.0.0.0"); // cross-machine exposure, no token
        StreamOpsHttpServer refused = new StreamOpsHttpServer(config, null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, refused::start,
                "non-loopbind bind without token must fail fast");
        assertTrue(String.valueOf(ex).contains("auth token"),
                "refusal explains the required token config: " + ex);
        assertFalse(refused.isRunning());
    }

    @Test
    void tokenConfiguredServerAnswers401WithoutCredentialsAndServesWithThem() throws Exception {
        StreamOpsConfig config = new StreamOpsConfig();
        config.setEnabled(true);
        config.setPort(0);
        config.setBindAddress("127.0.0.1");
        config.setAuthToken("secret-token-42");
        server = new StreamOpsHttpServer(config, null);
        server.start();
        int port = server.getBoundPort();

        // no credentials → structured 401 on every entry point (never silent pass-through)
        HttpResponse<String> noHeader = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noHeader.statusCode());
        assertTrue(noHeader.body().contains("UNAUTHORIZED"),
                "structured error body: " + noHeader.body());

        HttpResponse<String> wrongToken = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics"))
                        .header("Authorization", "Bearer wrong").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, wrongToken.statusCode());

        // unknown paths are also behind the gate (no unauthenticated path enumeration)
        HttpResponse<String> unknown = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/anything")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unknown.statusCode(), "catch-all is behind the auth gate too");

        // correct credentials → served
        HttpResponse<String> ok = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics"))
                        .header("Authorization", "Bearer secret-token-42").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ok.statusCode());
    }

    @Test
    void loopbackDefaultKeepsZeroAuthBehavior() throws Exception {
        // Back-compat: the historical zero-auth loopback default still starts and serves
        // without any Authorization header (also pinned by the existing 6 tests of
        // TestStreamOpsHttpServer — this asserts the config face explicitly).
        StreamOpsConfig config = new StreamOpsConfig();
        config.setEnabled(true);
        config.setPort(0);
        assertTrue(config.isLoopbackBind(), "default bind 127.0.0.1 is loopback");
        assertFalse(config.isAuthRequired(), "no token configured by default");

        server = new StreamOpsHttpServer(config, null);
        server.start();
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getBoundPort()
                        + "/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "loopback default serves without credentials");
    }

    @Test
    void tokenIsParseableFromProperties() {
        StreamOpsConfig config = StreamOpsConfig.fromProperties(key -> {
            if (StreamOpsConfig.KEY_AUTH_TOKEN.equals(key)) {
                return " from-properties ";
            }
            return null;
        });
        assertEquals("from-properties", config.getAuthToken(), "token is trimmed");
        assertTrue(config.isAuthRequired());
    }
}
