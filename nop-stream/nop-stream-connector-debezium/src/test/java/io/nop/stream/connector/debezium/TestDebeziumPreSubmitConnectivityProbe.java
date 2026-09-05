/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.connector.ConnectivityProbeOutcome;
import io.nop.stream.core.connector.StreamConnectivityProber;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 20 (P-REQ-13, D3-⑤) debezium family probe tests: construction/parameter level
 * only — the probe validates parameterization (connector name) and credential
 * references WITHOUT starting the Debezium engine and WITHOUT connecting to the
 * database (engine startup semantics cannot be reached non-invasively; {@code run()}+
 * {@code cancel()} was adjudicated out because it spins up a real engine).
 */
public class TestDebeziumPreSubmitConnectivityProbe {

    @Test
    public void probePassesForFullyParameterizedConfig() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("probe-ok");
        config.setConnectorType("mysql");
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("debezium-cdc", source);
        assertEquals(ConnectivityProbeOutcome.Status.PASS, outcome.getStatus(), () -> String.valueOf(outcome));
        assertDoesNotThrow(source::checkConnection);
    }

    @Test
    public void probeFailsExplicitlyWhenConnectorNameMissing() {
        DebeziumConfig config = new DebeziumConfig();
        config.setConnectorType("mysql");
        // name intentionally left unset
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);
        StreamException ex = assertThrows(StreamException.class, source::checkConnection);
        assertEquals("nop.err.stream.config-error", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("connector name is required"), ex.getMessage());

        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("debezium-cdc", source);
        assertEquals(ConnectivityProbeOutcome.Status.FAIL, outcome.getStatus());
        assertEquals("nop.err.stream.connectivity-check-failed", outcome.getErrorCode());
        assertTrue(outcome.getDetail().contains("connector name is required"), () -> outcome.getDetail());
    }

    @Test
    public void probeDoesNotStartEngine() {
        // The probe path must never touch run(): an engine-starting probe would need a
        // real database. A fully parameterized config with unreachable host still passes
        // the parameter-level probe — this pins the D3-⑤ depth boundary.
        DebeziumConfig config = new DebeziumConfig();
        config.setName("probe-no-engine");
        config.setConnectorType("mysql");
        config.setDatabaseHost("no-such-host.invalid");
        config.setDatabaseUser("u");
        config.setDatabasePassword("p");
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config) {
            @Override
            protected io.nop.message.debezium.DebeziumMessageSource createMessageSource(
                    DebeziumConfig cfg,
                    io.nop.message.debezium.engine.NopStreamOffsetBackingStore offsetStore) {
                throw new IllegalStateException("probe must not construct the Debezium engine");
            }
        };
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe("debezium-cdc", source);
        assertEquals(ConnectivityProbeOutcome.Status.PASS, outcome.getStatus(), () -> String.valueOf(outcome));
    }
}
