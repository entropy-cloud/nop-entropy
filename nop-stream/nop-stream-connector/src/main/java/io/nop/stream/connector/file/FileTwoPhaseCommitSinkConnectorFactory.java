/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorMaturity;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.IStreamSinkFactory;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.common.functions.SinkFunction;

import java.nio.charset.Charset;
import java.util.List;

import static io.nop.stream.core.connector.registry.ConnectorParamKind.INT;
import static io.nop.stream.core.connector.registry.ConnectorParamKind.STRING;

/**
 * SPI factory for the {@code file} sink type: constructs the exactly-once
 * {@link FileTwoPhaseCommitSink} (item 19 / P-REQ-28). Registered as a stateless bean in
 * {@code _vfs/nop/stream/beans/connector-file.beans.xml}.
 */
public final class FileTwoPhaseCommitSinkConnectorFactory implements IStreamSinkFactory {

    public static final String TYPE_NAME = "file";

    private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
            .sink(TYPE_NAME, FileTwoPhaseCommitSink.class.getName())
            .sinkConsistency(SinkConsistencyCapability.TWO_PHASE_COMMIT)
            .parallelism(ConnectorParallelism.PARALLEL)
            .recoverySemantic(ConnectorRecoverySemantic.TWO_PHASE_PENDING_COMMITS)
            .params(List.of(
                    ConnectorParamDescriptor.required("outputDir", STRING,
                            "output directory for per-epoch committed files and manifest"),
                    ConnectorParamDescriptor.optional("charset", STRING,
                            "text charset name, defaults to UTF-8"),
                    ConnectorParamDescriptor.optional("ledgerRetentionDays", INT,
                            "plan 2511 C3-002 / R01 epochledger face: manifest retention in days"
                                    + " (SSOT ledger-retention-policy §3.1 default 30; 0 disables"
                                    + " manifest pruning)"),
                    ConnectorParamDescriptor.optional("checkpointIntervalMs", INT,
                            "plan 2511 C3-002: the deployment checkpoint interval used to convert"
                                    + " retention-days into the manifest retention epoch window"
                                    + " (defaults to CheckpointConfig.DEFAULT_CHECKPOINT_INTERVAL)"),
                    ConnectorParamDescriptor.optional("maxEpochLines", INT,
                            "CRA 8.43 R4-03 W-04: current-epoch line-buffer bound "
                                    + "(default 100000). invoke fails typed once reached"),
                    ConnectorParamDescriptor.optional("maxPendingEpochs", INT,
                            "CRA 8.43 R4-03 W-04: uncommitted-epoch stack bound "
                                    + "(default 8 — each entry pins a tmp file; saveState fails "
                                    + "typed once reached)"),
                    ConnectorParamDescriptor.optional("manifestLockTimeoutMs", INT,
                            "CRA 8.43 R4-03 W-06: bounded wait for BOTH manifest-lock levels "
                                    + "(default 60000ms; cross-JVM exactly-once commit no longer "
                                    + "blocks forever on a hung peer JVM writer)")))
            .maturity(ConnectorMaturity.PRODUCTION)
            .build();

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public ConnectorCapabilityDescriptor describeCapabilities() {
        return DESCRIPTOR;
    }

    @Override
    public SinkFunction<?> createSink(StreamConnectorConfig config) {
        String outputDir = config.requireString("outputDir");
        String charsetName = config.getString("charset");
        Charset charset = charsetName == null ? null : Charset.forName(charsetName);
        // C3-002 (R01 epochledger face, form B): retention-days × checkpoint interval
        // → retention epoch window, computed once at assembly; zero new config keys
        // (the SSOT nop.stream.ledger.epochledger.retention-days value is projected in
        // by the deployment when it differs from the SSOT default)
        Integer retentionDays = config.getInt("ledgerRetentionDays");
        Integer checkpointIntervalMs = config.getInt("checkpointIntervalMs");
        if (retentionDays == null && checkpointIntervalMs == null) {
            return new FileTwoPhaseCommitSink<>(outputDir, charset);
        }
        long window = FileTwoPhaseCommitSink.computeRetentionEpochWindow(
                retentionDays != null ? retentionDays : 30L,
                checkpointIntervalMs != null ? checkpointIntervalMs.longValue()
                        : CheckpointConfig.DEFAULT_CHECKPOINT_INTERVAL);
        // CRA 8.43 R4-03 W-04/W-06: optional envelope/lock-budget knobs (connector
        // parameter domain — deliberately NOT in the nop.stream.ledger.* face space).
        Integer maxEpochLines = config.getInt("maxEpochLines");
        Integer maxPendingEpochs = config.getInt("maxPendingEpochs");
        Integer manifestLockTimeoutMs = config.getInt("manifestLockTimeoutMs");
        if (maxEpochLines == null && maxPendingEpochs == null && manifestLockTimeoutMs == null) {
            return new FileTwoPhaseCommitSink<>(outputDir, charset, 0, window);
        }
        return new FileTwoPhaseCommitSink<>(outputDir, charset, 0, window,
                maxEpochLines != null ? maxEpochLines.longValue()
                        : FileTwoPhaseCommitSink.DEFAULT_MAX_EPOCH_LINES,
                maxPendingEpochs != null ? maxPendingEpochs.longValue()
                        : FileTwoPhaseCommitSink.DEFAULT_MAX_PENDING_EPOCHS,
                manifestLockTimeoutMs != null ? manifestLockTimeoutMs.longValue()
                        : FileTwoPhaseCommitSink.DEFAULT_MANIFEST_LOCK_TIMEOUT_MS);
    }
}
