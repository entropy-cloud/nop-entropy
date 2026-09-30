/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.runtime.transport.DataPlaneMessageServiceAdapter;

/**
 * C4 (plan 369): resolves the transport persistence declaration tier for the
 * runtime deployment wiring. The durability of a data-plane transport is a
 * fact only the runtime layer that injects the {@link IMessageService} knows —
 * the core {@link CheckpointConfig} declaration defaults to the conservative
 * {@link CheckpointConfig.TransportPersistence#DURABLE}, and the executors that
 * build a checkpoint config next to an actual transport overwrite it here.
 *
 * <p>Classification:
 * <ul>
 *   <li>{@link LocalMessageService} (in-memory broker) → NON_DURABLE:
 *       in-flight records are lost when the hosting process fails, so the
 *       output-side in-flight data of an unaligned checkpoint cannot be relied
 *       on across a recovery.</li>
 *   <li>Everything else → DURABLE (conservative default): persistent brokers
 *       (JDBC polling, Kafka, Pulsar, DB-backed) are durable; an UNKNOWN
 *       transport is declared durable rather than silently unlocking the
 *       NON_DURABLE semantics.</li>
 * </ul>
 *
 * <p>Note: the current distributed plan builders
 * ({@code RemoteGraphExecutionPlanBuilder.buildRemoteOnly}) do not thread the
 * unaligned-checkpoint flags into the remote {@code InputGate}s, so the
 * fail-fast in {@code CheckpointConfig.validateUnalignedConfig()} currently
 * fires on the LOCAL launch chain. When unaligned is ever threaded into the
 * remote plans, the executors MUST call {@code validateUnalignedConfig()} on
 * the config they filled here so this declaration gains its startup teeth on
 * the distributed path too.
 */
@Internal
public final class TransportDurability {

    private TransportDurability() {
    }

    /**
     * Resolves the persistence tier of the given transport service.
     *
     * @param service the injected transport service (nullable → durable default)
     * @return the declared {@link CheckpointConfig.TransportPersistence} tier
     */
    public static CheckpointConfig.TransportPersistence resolve(IMessageService service) {
        IMessageService s = service;
        // The data-plane adapter is a view over the raw service; classify the
        // underlying transport.
        while (s instanceof DataPlaneMessageServiceAdapter) {
            s = ((DataPlaneMessageServiceAdapter) s).getDelegate();
        }
        if (s instanceof LocalMessageService) {
            return CheckpointConfig.TransportPersistence.NON_DURABLE;
        }
        return CheckpointConfig.TransportPersistence.DURABLE;
    }
}
