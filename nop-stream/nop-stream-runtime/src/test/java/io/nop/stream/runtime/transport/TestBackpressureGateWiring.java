/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.message.core.channel.ChannelBacklogProbe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * WI6 (plan 2331) closure-audit M1(a): the builder ASSEMBLY chain — adapter unwrap +
 * system-property limit resolution — exercised directly (the gated SysDao E2E wires
 * the probe manually, so without this test the unwrap chain would be code-walk-only).
 */
public class TestBackpressureGateWiring {

    @Test
    public void adapterDelegateIsUnwrappedForCapabilityLookup() {
        IMessageServiceWithProbe raw = new IMessageServiceWithProbe(topic -> 1L);
        DataPlaneMessageServiceAdapter adapter = new DataPlaneMessageServiceAdapter(
                raw, SysDaoWireCodec.INSTANCE);
        RemoteGraphExecutionPlanBuilder builder = new RemoteGraphExecutionPlanBuilder(
                adapter, new io.nop.stream.core.execution.transport.TypeRegistry(), 1L);
        ChannelBacklogProbe resolved = builder.resolveBacklogProbe();
        assertNotNull(resolved, "probe behind the codec adapter must be found via getDelegate()");
        // the RESOLVED capability is the raw backend itself (which implements the
        // interface) — proving the lookup went through getDelegate(), not the adapter
        assertSame(raw, resolved);
    }

    @Test
    public void serviceWithoutCapabilityResolvesNull() {
        // plain LocalMessageService implements no probe capability
        DataPlaneMessageServiceAdapter adapter = new DataPlaneMessageServiceAdapter(
                new io.nop.message.core.local.LocalMessageService(), SysDaoWireCodec.INSTANCE);
        RemoteGraphExecutionPlanBuilder builder = new RemoteGraphExecutionPlanBuilder(
                adapter, new io.nop.stream.core.execution.transport.TypeRegistry(), 1L);
        assertNull(builder.resolveBacklogProbe(),
                "a backend without ChannelBacklogProbe resolves to null (gate disabled)");
    }

    @Test
    public void limitComesFromSystemPropertyWithZeroDefault() {
        RemoteGraphExecutionPlanBuilder builder = new RemoteGraphExecutionPlanBuilder(
                new IMessageServiceWithProbe(null),
                new io.nop.stream.core.execution.transport.TypeRegistry(), 1L);
        assertEquals(0L, builder.resolveDbBacklogLimit(), "unset = gate off");
        System.setProperty("nop.stream.data.db.backlog.limit", "1234");
        try {
            assertEquals(1234L, builder.resolveDbBacklogLimit());
        } finally {
            System.clearProperty("nop.stream.data.db.backlog.limit");
        }
    }

    /** Minimal raw backend carrying (or not) the probe capability. */
    static final class IMessageServiceWithProbe
            extends io.nop.message.core.local.LocalMessageService
            implements ChannelBacklogProbe {
        private final ChannelBacklogProbe probe;

        IMessageServiceWithProbe(ChannelBacklogProbe probe) {
            this.probe = probe;
        }

        @Override
        public long backlog(String topic) {
            return probe == null ? 0L : probe.backlog(topic);
        }
    }
}
