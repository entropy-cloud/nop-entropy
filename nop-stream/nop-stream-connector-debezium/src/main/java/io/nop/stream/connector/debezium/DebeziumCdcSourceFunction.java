/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.nop.api.core.util.ICancellable;
import io.nop.credential.api.ICredentialProvider;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.connector.DrainableSource;
import io.nop.stream.core.credentials.StreamCredentialSupport;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_STATE_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONFIG_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * CDC source function wrapping the embedded Debezium engine.
 *
 * <p>Implements {@link CheckpointedSourceFunction} so the CDC consumption offset participates in
 * the nop-stream checkpoint protocol. On checkpoint the Debezium offset map held by a
 * {@link NopStreamOffsetBackingStore} is snapshotted into the operator state under key
 * {@value #CDC_OFFSETS_KEY}; on recovery the offset map is restored into a freshly created store
 * so the engine resumes from the checkpointed position (no duplicates, no data loss).
 *
 * <p>See {@code ai-dev/design/nop-stream/connector-design.md} §5.4 for the full design rationale.
 */
public class DebeziumCdcSourceFunction implements DrainableSource<ChangeEvent>,
        CheckpointedSourceFunction<ChangeEvent>, ConnectivityCheckable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(DebeziumCdcSourceFunction.class);

    /**
     * Operator-state key under which the CDC offset map is persisted in the checkpoint snapshot.
     */
    public static final String CDC_OFFSETS_KEY = "cdc-offsets";

    /**
     * Debezium connector configuration. No longer {@code transient}: {@link DebeziumConfig}
     * implements {@link java.io.Serializable}, so the connection info survives cross-JVM recovery.
     * Credential fields may hold {@code credential:{id}#{field}} REFERENCES — the reference
     * string (not plaintext) is what persists here and in every serialization path (D4).
     */
    private DebeziumConfig config;

    /**
     * Item 20 (P-REQ-14, D4): the platform credential decryption point, injected per
     * JVM assembly (constructor or {@link #setCredentialProvider}). {@code transient}
     * on purpose: the function's Java-serialization path (deployment descriptor /
     * checkpoint recovery) must not carry the provider; each JVM re-injects it. When
     * the config carries credential references and this field is null, decryption
     * fails closed (typed error, never silent empty).
     */
    private transient volatile ICredentialProvider credentialProvider;

    private volatile boolean running = true;
    private volatile boolean draining = false;
    private final AtomicBoolean runEntered = new AtomicBoolean(false);
    private transient volatile CountDownLatch completionLatch;
    private volatile DebeziumMessageSource source;
    private volatile ICancellable subscription;

    /**
     * Offset backing store backing the checkpoint round-trip. {@code transient} because it is
     * rebuilt by {@link #initializeState(TaskStateSnapshot)} on recovery (the offset data itself
     * is carried in the checkpoint, not in the serialized source instance).
     */
    private transient NopStreamOffsetBackingStore offsetStore;

    public DebeziumCdcSourceFunction(DebeziumConfig config) {
        this(config, null);
    }

    /**
     * Item 20 (P-REQ-14, D4): full constructor with the platform credential provider.
     * The provider decrypts {@code credential:{id}#{field}} references on the
     * engine-side transient path only — the original config (and therefore every
     * serialization/checkpoint path) keeps the reference string, never plaintext.
     */
    public DebeziumCdcSourceFunction(DebeziumConfig config, ICredentialProvider credentialProvider) {
        if (config == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "config");
        }
        this.config = config;
        this.credentialProvider = credentialProvider;
        this.completionLatch = new CountDownLatch(1);
    }

    /**
     * Per-JVM (re-)injection point for the credential provider, e.g. after Java
     * deserialization of the deployment pipeline spec (the transient field is null
     * on the deserialized instance until the host injects it).
     */
    public void setCredentialProvider(ICredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    /** Test visibility: the persistent config (credential REFERENCE strings, not plaintext). */
    DebeziumConfig getConfigForTest() {
        return config;
    }

    /** Test visibility: the transient per-JVM credential provider. */
    ICredentialProvider getCredentialProviderForTest() {
        return credentialProvider;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (config == null) {
            config = new DebeziumConfig();
        }
        completionLatch = new CountDownLatch(1);
    }

    /**
     * Returns the offset store currently bound to this source (may be null before
     * {@link #initializeState(TaskStateSnapshot)} runs). Primarily for tests.
     */
    public NopStreamOffsetBackingStore getOffsetStore() {
        return offsetStore;
    }

    /**
     * Always creates a fresh completion latch. Reusing the previous run's latch is
     * unsafe: {@link #cancel()} already counted it down, so {@code await} would return
     * immediately and the run loop would exit (silent EOS) on a region-restart re-run.
     */
    private void initCompletionLatch() {
        this.completionLatch = new CountDownLatch(1);
    }

    /**
     * Creates the {@link DebeziumMessageSource} used by {@link #run(SourceContext)}. Protected so
     * tests can inject a mock/test-double source without spinning up a real Debezium engine.
     *
     * @param config      the Debezium configuration
     * @param offsetStore the offset store (may be null on first run with no prior checkpoint)
     * @return a new message source wired to the offset store
     */
    protected DebeziumMessageSource createMessageSource(DebeziumConfig config,
                                                        NopStreamOffsetBackingStore offsetStore) {
        return new DebeziumMessageSource(config, offsetStore);
    }

    @Override
    public void run(SourceContext<ChangeEvent> ctx) throws Exception {
        if (!runEntered.compareAndSet(false, true)) {
            return;
        }
        // Reset lifecycle state: a region restart reuses this instance (the rebuilt
        // operator chain shares the source function) after cancel() set running=false.
        // Without this reset the loop below exits immediately and the CDC source is
        // silently treated as EOS (missed changes, no error).
        this.running = true;
        this.draining = false;
        initCompletionLatch();

        try {
            if (!draining) {
                source = createMessageSource(effectiveEngineConfig(), offsetStore);
                try {
                    subscription = source.subscribe(ctx::collect);
                } catch (Exception e) {
                    source.stop();
                    throw e;
                }
            }

            while (running && !draining) {
                if (completionLatch.await(1, TimeUnit.SECONDS)) {
                    break;
                }
            }
        } finally {
            if (subscription != null) {
                try {
                    subscription.cancel();
                } catch (Exception e) {
                    // cleanup path: never masks the primary result, but stays observable
                    LOG.warn("Failed to cancel CDC subscription during cleanup", e);
                }
                subscription = null;
            }
            if (source != null) {
                try {
                    source.stop();
                } catch (Exception e) {
                    // cleanup path: never masks the primary result, but stays observable
                    LOG.warn("Failed to stop Debezium message source during cleanup", e);
                }
                source = null;
            }
            runEntered.set(false);
        }
    }

    @Override
    public void cancel() {
        running = false;
        if (completionLatch != null) {
            completionLatch.countDown();
        }
        ICancellable sub = this.subscription;
        if (sub != null) {
            sub.cancel();
        }
        DebeziumMessageSource msgSource = this.source;
        if (msgSource != null) {
            msgSource.stop();
        }
    }

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        return SourceConsistencyCapability.REPLAYABLE;
    }

    @Override
    public void truncateForDrain() throws Exception {
        draining = true;
        ICancellable sub = this.subscription;
        if (sub != null) {
            sub.cancel();
            subscription = null;
        }
        DebeziumMessageSource msgSource = this.source;
        if (msgSource != null) {
            msgSource.stop();
            source = null;
        }
        if (completionLatch != null) {
            completionLatch.countDown();
        }
    }

    public boolean isDraining() {
        return draining;
    }

    // ---- CheckpointedSourceFunction ----

    @Override
    public OperatorSnapshotResult snapshotState(long checkpointId) throws Exception {
        OperatorSnapshotResult result = new OperatorSnapshotResult();
        result.setCheckpointId(checkpointId);

        NopStreamOffsetBackingStore store = this.offsetStore;
        if (store == null) {
            // No offset store bound (e.g. snapshot before run/initializeState). Persist an empty
            // offset map so restore sees a well-formed entry rather than a missing one.
            result.putOperatorState(CDC_OFFSETS_KEY, new java.util.TreeMap<>());
            return result;
        }

        Map<java.nio.ByteBuffer, java.nio.ByteBuffer> offsets = store.getOffsets();
        result.putOperatorState(CDC_OFFSETS_KEY, NopStreamOffsetBackingStore.toSerializable(offsets));
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initializeState(TaskStateSnapshot state) throws Exception {
        if (state == null) {
            // First run: no prior checkpoint. Clear any stale static-registry offset left by a
            // previous run in the same JVM (crashed run / redeploy / another pipeline reusing
            // the connector name), then create an empty store so the engine starts fresh.
            this.offsetStore = newFreshOffsetStore();
            return;
        }

        Object raw = state.getOperatorState(CDC_OFFSETS_KEY);
        if (raw == null) {
            // Prior checkpoint existed but carried no CDC offset entry. Start fresh rather than
            // silently dropping the offset: clear stale static-registry offsets and bind an
            // empty store.
            this.offsetStore = newFreshOffsetStore();
            return;
        }

        if (!(raw instanceof Map)) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_STATE_NAME, CDC_OFFSETS_KEY)
                    .param(ARG_DETAIL, "CDC offset state is not a Map: " + raw.getClass().getName());
        }

        Map<String, String> serialized = (Map<String, String>) raw;
        Map<java.nio.ByteBuffer, java.nio.ByteBuffer> restored =
                NopStreamOffsetBackingStore.fromSerializable(serialized);

        this.offsetStore = NopStreamOffsetBackingStore.forConnector(resolveConnectorName());
        this.offsetStore.setOffsets(restored);
    }

    /**
     * AR-03: first-run paths must never inherit the static offset registry entry left by a
     * previous run in the same JVM. Clearing the entry before binding forces the engine to start
     * from the beginning (fresh snapshot) instead of silently resuming from a stale offset.
     */
    private NopStreamOffsetBackingStore newFreshOffsetStore() {
        String name = resolveConnectorName();
        NopStreamOffsetBackingStore.clearConnector(name);
        return NopStreamOffsetBackingStore.forConnector(name);
    }

    private String resolveConnectorName() {
        DebeziumConfig cfg = this.config;
        if (cfg != null && cfg.getName() != null && !cfg.getName().isEmpty()) {
            return cfg.getName();
        }
        // AR-03: unnamed connectors silently share the "_default_" offset bucket and overwrite
        // each other's offsets in the same JVM. Fail fast with configuration guidance.
        throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                .param(ARG_DETAIL,
                        "Debezium connector name is required: set DebeziumConfig.name so the offset "
                                + "registry bucket is unique per connector (unnamed connectors share "
                                + "the '_default_' bucket and silently overwrite each other's offsets)");
    }

    /**
     * Item 20 (P-REQ-13, D3-⑤): pre-submit probe at construction/parameter level —
     * validates that the connector is fully parameterized (connector name present,
     * {@link #resolveConnectorName()} typed fail-fast) and that any credential
     * references on the connection fields are resolvable (D4, fail-closed). It does
     * NOT start the Debezium engine and does NOT open a database connection: the
     * Debezium connection semantics belong to the engine startup phase and cannot be
     * reached without invasive side effects (design adjudication; {@code run()}+
     * {@code cancel()} was rejected because it spins up a real engine).
     */
    @Override
    public void checkConnection() {
        if (config == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "config");
        }
        resolveConnectorName();
        verifyCredentialReferences();
    }

    // ------------------------------------------------------------------
    // Credential reference handling (D4: reference persists, transient decrypt)
    // ------------------------------------------------------------------

    private static final String FIELD_DATABASE_USER = "databaseUser";
    private static final String FIELD_DATABASE_PASSWORD = "databasePassword";

    private boolean hasCredentialReferences() {
        return StreamCredentialSupport.isCredentialReference(config.getDatabaseUser())
                || StreamCredentialSupport.isCredentialReference(config.getDatabasePassword());
    }

    /**
     * Credential reachability check for dry-run (D4): a successful decryption IS the
     * reachability verdict — deliberately NOT {@code testCredential()} (the platform
     * stub always returns {@code success=false}; depending on it would produce a
     * systematic false negative). Plaintext is never logged or reported.
     */
    private void verifyCredentialReferences() {
        String user = config.getDatabaseUser();
        if (StreamCredentialSupport.isCredentialReference(user)) {
            StreamCredentialSupport.resolve(user, FIELD_DATABASE_USER, credentialProvider);
        }
        String password = config.getDatabasePassword();
        if (StreamCredentialSupport.isCredentialReference(password)) {
            StreamCredentialSupport.resolve(password, FIELD_DATABASE_PASSWORD, credentialProvider);
        }
    }

    /**
     * The config handed to the engine: the ORIGINAL config when it carries no
     * credential references; otherwise a TRANSIENT DECRYPTED COPY (built via
     * serialization round-trip so every field survives) whose user/password fields
     * are resolved plaintext. The plaintext lives only inside this method's local
     * path and the engine instance it constructs — the original config object and
     * every serialization path keep the reference string (D4: reference persists,
     * decrypt is engine-side transient).
     */
    private DebeziumConfig effectiveEngineConfig() {
        DebeziumConfig cfg = this.config;
        if (!hasCredentialReferences()) {
            return cfg;
        }
        DebeziumConfig copy = serializationRoundTripCopy(cfg);
        String user = cfg.getDatabaseUser();
        if (StreamCredentialSupport.isCredentialReference(user)) {
            copy.setDatabaseUser(StreamCredentialSupport.resolve(user, FIELD_DATABASE_USER, credentialProvider));
        }
        String password = cfg.getDatabasePassword();
        if (StreamCredentialSupport.isCredentialReference(password)) {
            copy.setDatabasePassword(
                    StreamCredentialSupport.resolve(password, FIELD_DATABASE_PASSWORD, credentialProvider));
        }
        return copy;
    }

    private static DebeziumConfig serializationRoundTripCopy(DebeziumConfig cfg) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
                oos.writeObject(cfg);
            }
            try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                    new java.io.ByteArrayInputStream(bos.toByteArray()))) {
                return (DebeziumConfig) ois.readObject();
            }
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_CONFIG_ERROR, e)
                    .param(ARG_DETAIL, "failed to build the transient decrypted config copy");
        }
    }
}
