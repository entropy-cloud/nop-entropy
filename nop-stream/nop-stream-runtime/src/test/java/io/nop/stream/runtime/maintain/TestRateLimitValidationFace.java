/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.maintain;

import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.connector.MessageSourceConnectorFactory;
import io.nop.stream.core.connector.registry.ConnectorDirection;
import io.nop.stream.core.connector.registry.StreamConnectorCatalog;
import io.nop.stream.core.connector.registry.StreamConnectorRegistry;
import io.nop.stream.flow.validate.StreamConfValidationReport;
import io.nop.stream.flow.validate.StreamConfValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5 (plan 2330): the single-writer / no-rate-contract sources keep rate-free
 * descriptors — configuring a rate through the connector-validation face is an
 * unknown-param typed error (the honest "typed unsupported" surface; the programmatic
 * construction face deliberately does not validate unknown params, plan 2330 baseline).
 * Hosted in runtime because only this module sees both the flow validator and the
 * connector factories.
 */
public class TestRateLimitValidationFace {

    @Test
    public void rateParamOnMessageSourceIsUnknownParamTypedError() {
        StreamConnectorCatalog catalog = StreamConnectorCatalog.of(
                StreamConnectorRegistry.of(List.of(new MessageSourceConnectorFactory())));
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SOURCE, "message",
                Map.of("topic", "t", "messageService", new LocalMessageService(),
                        "rateLimitRowsPerSecond", "100"),
                catalog, false);
        assertEquals(1, report.getExitCode());
        assertTrue(report.getIssues().stream().anyMatch(
                        i -> "nop.err.stream.connector-param-unknown".equals(i.getErrorCode())),
                () -> report.render());
    }

    @Test
    public void rateParamOnFileSourceIsValidAndUnknownPolicyRejected() {
        StreamConnectorCatalog catalog = StreamConnectorCatalog.of(
                StreamConnectorRegistry.of(List.of(
                        new io.nop.stream.connector.file.FileSourceConnectorFactory())));
        // legal policy passes the rule check (construction may still need the dir)
        StreamConfValidationReport report = new StreamConfValidator().validateConnector(
                ConnectorDirection.SOURCE, "file",
                Map.of("directoryPath", "/tmp/whatever",
                        "rateLimitRowsPerSecond", "100",
                        "splitAssignmentPolicy", "size-aware"),
                catalog, false);
        assertTrue(report.isPassed(), () -> report.render());
        // unknown policy rejected by the allowedValues rule
        StreamConfValidationReport bad = new StreamConfValidator().validateConnector(
                ConnectorDirection.SOURCE, "file",
                Map.of("directoryPath", "/tmp/whatever", "splitAssignmentPolicy", "fastest"),
                catalog, false);
        assertEquals(1, bad.getExitCode());
        assertTrue(bad.getIssues().stream().anyMatch(
                        i -> "nop.err.stream.connector-param-value-disallowed".equals(i.getErrorCode())),
                () -> bad.render());
    }
}
