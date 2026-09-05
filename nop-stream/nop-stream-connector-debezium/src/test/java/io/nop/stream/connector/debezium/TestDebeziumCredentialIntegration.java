/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.stream.core.connector.ConnectivityProbeOutcome;
import io.nop.stream.core.connector.StreamConnectivityProber;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 20 (P-REQ-14, D4) credential integration tests for the CDC family: the
 * {@code credential:{id}#{field}} reference persists in the Serializable config
 * while decryption happens only on the engine-side transient path; fail-closed on
 * missing provider / unknown credential / malformed reference; the serialization
 * path never carries plaintext (aligned with {@code testConfigSurvivesSerialization}).
 */
public class TestDebeziumCredentialIntegration {

    private static final String SECRET = "s3cr3t-plaintext";

    /** Map-backed provider: same fail-closed shape as the platform implementation. */
    private static final class FakeCredentialProvider implements ICredentialProvider {
        @Override
        public io.nop.credential.api.CredentialData getCredential(String credentialId) {
            throw credentialMissing(credentialId);
        }

        @Override
        public Object getCredentialData(String credentialId, String field) {
            if ("mysql-prod".equals(credentialId) && "password".equals(field)) {
                return SECRET;
            }
            throw credentialMissing(credentialId);
        }

        @Override
        public TestResult testCredential(String credentialId) {
            // W2 platform stub semantics — deliberately NOT used by stream-side probes.
            return new TestResult(false, "test not implemented", new Timestamp(System.currentTimeMillis()));
        }

        @Override
        public MaskedCredential mask(String credentialId) {
            throw credentialMissing(credentialId);
        }

        @Override
        public void registerUsage(String credentialId, String consumerRef) {
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
        }

        private static NopException credentialMissing(String credentialId) {
            NopException ex = new NopException(io.nop.api.core.ApiErrors.ERR_WRAP_EXCEPTION);
            ex.param("credentialId", credentialId);
            ex.description("credential does not exist or has been soft-deleted");
            return ex;
        }
    }

    private static DebeziumConfig refConfig() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("cred-integration");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        config.setDatabaseUser("app-user");
        config.setDatabasePassword("credential:mysql-prod#password");
        return config;
    }

    // ------------------------------------------------------------------
    // engine-side transient decryption
    // ------------------------------------------------------------------

    @Test
    void engineReceivesTransientDecryptedCopyOriginalKeepsReference() throws Exception {
        AtomicReference<DebeziumConfig> engineConfig = new AtomicReference<>();
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(refConfig(),
                new FakeCredentialProvider()) {
            @Override
            protected DebeziumMessageSource createMessageSource(DebeziumConfig cfg,
                                                                 NopStreamOffsetBackingStore store) {
                engineConfig.set(cfg);
                return new NoEngineMessageSource(cfg);
            }
        };
        runUntilEngineCreated(source, engineConfig);
        DebeziumConfig captured = engineConfig.get();
        assertEquals(SECRET, captured.getDatabasePassword(),
                "engine must receive the decrypted plaintext (transient copy)");
        assertEquals("app-user", captured.getDatabaseUser(), "non-reference fields pass through");
        assertEquals("credential:mysql-prod#password", source.getConfigForTest().getDatabasePassword(),
                "the ORIGINAL config keeps the reference string (D4: reference persists)");
        assertNotEquals(captured, source.getConfigForTest());
        source.cancel();
    }

    @Test
    void plainPasswordConfigNeedsNoProviderAndNoCopy() throws Exception {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("plain");
        config.setConnectorType("mysql");
        config.setDatabasePassword("plainpw");
        AtomicReference<DebeziumConfig> engineConfig = new AtomicReference<>();
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config) {
            @Override
            protected DebeziumMessageSource createMessageSource(DebeziumConfig cfg,
                                                                 NopStreamOffsetBackingStore store) {
                engineConfig.set(cfg);
                return new NoEngineMessageSource(cfg);
            }
        };
        runUntilEngineCreated(source, engineConfig);
        assertEquals("plainpw", engineConfig.get().getDatabasePassword());
        assertEquals(config, engineConfig.get(), "no reference → the SAME config instance is handed over");
        source.cancel();
    }

    // ------------------------------------------------------------------
    // fail-closed semantics
    // ------------------------------------------------------------------

    @Test
    void missingProviderFailsClosedOnBothProbeAndRun() throws Exception {
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(refConfig()); // provider = null
        StreamException probe = assertThrows(StreamException.class, source::checkConnection);
        assertEquals("nop.err.stream.credential-provider-missing", probe.getErrorCode());

        AtomicReference<Throwable> runError = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                source.run(new NopContext());
            } catch (Throwable t) {
                runError.set(t);
            }
        });
        runner.start();
        runner.join(5000);
        assertTrue(runError.get() instanceof StreamException
                        && "nop.err.stream.credential-provider-missing".equals(
                        ((StreamException) runError.get()).getErrorCode()),
                "run() must fail closed too, not start an engine without credentials: " + runError.get());
    }

    @Test
    void unknownCredentialFailsClosedWithTypedError() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("cred-unknown");
        config.setDatabasePassword("credential:no-such-cred#password");
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config, new FakeCredentialProvider());
        StreamException ex = assertThrows(StreamException.class, source::checkConnection);
        assertEquals("nop.err.stream.credential-unresolved", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("no-such-cred"), ex.getMessage());
    }

    @Test
    void malformedReferenceFailsClosedWithTypedError() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("cred-malformed");
        config.setDatabaseUser("credential:no-hash-separator");
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config, new FakeCredentialProvider());
        StreamException ex = assertThrows(StreamException.class, source::checkConnection);
        assertEquals("nop.err.stream.credential-ref-invalid", ex.getErrorCode());
    }

    @Test
    void resolvableReferencesPassTheDryRunProbe() {
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(refConfig(),
                new FakeCredentialProvider());
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("debezium-cdc", source);
        assertEquals(ConnectivityProbeOutcome.Status.PASS, outcome.getStatus(), () -> String.valueOf(outcome));
        assertDoesNotThrow(source::checkConnection);
    }

    @Test
    void dryRunProbeSurfacesCredentialFailureExplicitly() {
        // Phase 3 deferred case: the credential fail-closed path through the dry-run
        // driver must surface the explicit error code, never a silent pass.
        DebeziumConfig config = new DebeziumConfig();
        config.setName("cred-dryrun-fail");
        config.setDatabasePassword("credential:no-such-cred#password");
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config, new FakeCredentialProvider());
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("debezium-cdc", source);
        assertEquals(ConnectivityProbeOutcome.Status.FAIL, outcome.getStatus());
        assertEquals("nop.err.stream.connectivity-check-failed", outcome.getErrorCode());
        assertTrue(outcome.getDetail().contains("credential-unresolved")
                        || outcome.getDetail().contains("no-such-cred"),
                () -> outcome.getDetail());
    }

    // ------------------------------------------------------------------
    // serialization boundary (aligned with testConfigSurvivesSerialization)
    // ------------------------------------------------------------------

    @Test
    void serializationCarriesReferenceNotPlaintextAndProviderIsTransient() throws Exception {
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(refConfig(),
                new FakeCredentialProvider());
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(source);
        }
        String serialized = bos.toString(java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(serialized.contains("credential:mysql-prod#password"),
                "the reference string must survive serialization");
        assertFalse(serialized.contains(SECRET),
                "plaintext must NEVER appear in the serialized form (D4 boundary)");

        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bos.toByteArray()))) {
            DebeziumCdcSourceFunction restored = (DebeziumCdcSourceFunction) ois.readObject();
            assertEquals("credential:mysql-prod#password",
                    restored.getConfigForTest().getDatabasePassword(),
                    "restore keeps the reference (re-resolvable in the new JVM)");
            // transient provider: null after restore → fail-closed until re-injected
            StreamException fail = assertThrows(StreamException.class, restored::checkConnection);
            assertEquals("nop.err.stream.credential-provider-missing", fail.getErrorCode());
            restored.setCredentialProvider(new FakeCredentialProvider());
            assertDoesNotThrow(restored::checkConnection,
                    "per-JVM re-injection restores credential reachability");
        }
    }

    @Test
    void serializationWithoutReferencesKeepsPriorSemantics() throws Exception {
        // testConfigSurvivesSerialization alignment: a plain config still round-trips.
        DebeziumConfig config = new DebeziumConfig();
        config.setName("cred-plain-roundtrip");
        config.setDatabasePassword("plainpw");
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(source);
        }
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bos.toByteArray()))) {
            DebeziumCdcSourceFunction restored = (DebeziumCdcSourceFunction) ois.readObject();
            assertEquals("plainpw", restored.getConfigForTest().getDatabasePassword());
            assertNull(restored.getCredentialProviderForTest());
        }
    }

    // ------------------------------------------------------------------
    // helpers / fixtures
    // ------------------------------------------------------------------

    private static void runUntilEngineCreated(DebeziumCdcSourceFunction source,
                                              AtomicReference<DebeziumConfig> engineConfig) throws Exception {
        Thread runner = new Thread(() -> {
            try {
                source.run(new NopContext());
            } catch (Exception ignored) {
                // cancel path
            }
        });
        runner.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (engineConfig.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(engineConfig.get() != null, "engine must be created with the decrypted config");
    }

    private static final class NopContext implements io.nop.stream.core.common.functions.source
            .SourceFunction.SourceContext<ChangeEvent> {
        @Override
        public void collect(ChangeEvent element) {
        }

        @Override
        public void collectWithTimestamp(ChangeEvent element, long timestamp) {
        }

        @Override
        public void emitWatermark(long mark) {
        }

        @Override
        public void markAsTemporarilyIdle() {
        }

        @Override
        public long getProcessingTime() {
            return System.currentTimeMillis();
        }
    }

    /** No-engine DebeziumMessageSource test double (existing test pattern). */
    static final class NoEngineMessageSource extends DebeziumMessageSource {
        NoEngineMessageSource(DebeziumConfig config) {
            super(config, null);
        }

        @Override
        public io.nop.api.core.util.ICancellable subscribe(java.util.function.Consumer<ChangeEvent> action) {
            return new io.nop.api.core.util.ICancellable() {
                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public String getCancelReason() {
                    return null;
                }

                @Override
                public void cancel(String reason) {
                }

                @Override
                public void appendOnCancel(java.util.function.Consumer<String> task) {
                }

                @Override
                public void removeOnCancel(java.util.function.Consumer<String> task) {
                }
            };
        }
    }
}
