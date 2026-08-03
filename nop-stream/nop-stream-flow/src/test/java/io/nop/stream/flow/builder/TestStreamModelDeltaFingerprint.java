/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.flow.model.StreamTransformModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Stage 51 — Fingerprint sensitivity tests for Delta-customized
 * {@code .stream.xml} models.
 *
 * <p>Uses the real {@link StreamModel#computeFingerprint()} on
 * {@code io.nop.stream.core.model.StreamModel} (the executable canonical model),
 * constructed from the <b>parsed flow model's transform IDs</b> (the stable
 * string identifiers declared in the XML, e.g. {@code "src"}/{@code "upper"}/
 * {@code "out"}). This is the same approach used by the existing
 * {@code TestStreamModelFingerprint} / {@code TestFingerprintValidation} tests,
 * which construct the executable model with stable string keys and
 * (optionally) null transformation values — the {@code dagTopologyHash} is a
 * SHA-256 of the sorted transform-id keys, so it captures the DAG topology
 * identity regardless of the (auto-increment, unstable) runtime
 * {@code Transformation.id} integers.
 *
 * <h3>Two classes of delta</h3>
 * <ul>
 *   <li><b>Transform-level delta</b> (add/remove a transform): the set of
 *       transform IDs changes → {@code dagTopologyHash} changes → fingerprint
 *       changes. <b>Sensitive.</b></li>
 *   <li><b>Config-only delta</b> (change only checkpoint interval / parallelism,
 *       same transforms and edges): the transform-ID set is unchanged →
 *       {@code dagTopologyHash} unchanged → fingerprint unchanged.
 *       <b>By-design insensitive</b>: fingerprint captures DAG topology
 *       identity, not runtime configuration.</li>
 * </ul>
 *
 * <p><b>Note on the auto-increment {@code Transformation.id} caveat</b>:
 * Building two separately-parsed models through the full builder produces
 * transformation objects with globally-unique auto-increment IDs and default
 * {@code toString()} (see {@code TestDagTopologyConsistency}). Cross-build
 * fingerprint <em>equality</em> is therefore structurally impossible today —
 * this is the Stage 50 deferred successor (rewrite fingerprint based on
 * Transformation structure content). These tests sidestep that limitation by
 * constructing the executable model from the <b>parsed transform IDs</b>
 * (stable strings), so the fingerprint comparison reflects genuine topology
 * identity rather than auto-increment noise.
 */
public class TestStreamModelDeltaFingerprint {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void transformLevelDeltaProducesDifferentFingerprint() {
        io.nop.stream.flow.model.StreamModel base =
                parseStreamXml("/nop/stream/test/test-delta-base.stream.xml");
        io.nop.stream.flow.model.StreamModel delta =
                parseStreamXml("/nop/stream/test/test-delta-extends.stream.xml");

        StreamModelFingerprint baseFp = fingerprintOf(base);
        StreamModelFingerprint deltaFp = fingerprintOf(delta);

        assertNotEquals(baseFp, deltaFp,
                "A transform-level delta (adding deltaFilter) must change the fingerprint");
        assertNotEquals(baseFp.getDagTopologyHash(), deltaFp.getDagTopologyHash(),
                "The dagTopologyHash must differ because the delta adds a transform id");
    }

    @Test
    public void configOnlyDeltaPreservesFingerprintByDesign() {
        io.nop.stream.flow.model.StreamModel base =
                parseStreamXml("/nop/stream/test/test-delta-config-base.stream.xml");
        io.nop.stream.flow.model.StreamModel delta =
                parseStreamXml("/nop/stream/test/test-delta-config-extends.stream.xml");

        // Sanity: the delta genuinely changed only runtime config, not topology.
        assertEquals(transformIds(base), transformIds(delta),
                "Config-only delta must not change the set of transform ids");
        // checkpoint interval differs by construction (60000 -> 30000)
        assertEquals(60000L, base.getCheckpoint().getInterval());
        assertEquals(30000L, delta.getCheckpoint().getInterval());

        StreamModelFingerprint baseFp = fingerprintOf(base);
        StreamModelFingerprint deltaFp = fingerprintOf(delta);

        // By-design: fingerprint = DAG topology identity, NOT runtime config.
        // checkpoint interval / parallelism are not hashed by computeFingerprint().
        assertEquals(baseFp, deltaFp,
                "Config-only delta (checkpoint interval / parallelism) must NOT change "
                        + "the fingerprint — by-design: fingerprint = DAG topology identity");
        assertEquals(baseFp.getDagTopologyHash(), deltaFp.getDagTopologyHash());
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    /**
     * Compute the real {@link StreamModel#computeFingerprint()} on an executable
     * {@link StreamModel} constructed from the parsed flow model's transform IDs.
     * The transformation values are null (handled as the literal "null" string
     * by {@code computeFingerprint}), matching the pattern used by
     * {@code TestStreamModelFingerprint.testIsNotCompatibleWithDifferentDag}.
     * The {@code dagTopologyHash} depends only on the sorted transform-id keys.
     */
    private static StreamModelFingerprint fingerprintOf(io.nop.stream.flow.model.StreamModel flowModel) {
        StreamComponents components = new StreamComponents();
        Map<String, Transformation<?>> transformMap = new LinkedHashMap<>();
        for (StreamTransformModel t : flowModel.getTransforms()) {
            transformMap.put(t.getId(), null);
        }
        return new StreamModel(components, transformMap).computeFingerprint();
    }

    private static java.util.Set<String> transformIds(io.nop.stream.flow.model.StreamModel flowModel) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (StreamTransformModel t : flowModel.getTransforms()) {
            ids.add(t.getId());
        }
        return ids;
    }

    private static io.nop.stream.flow.model.StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (io.nop.stream.flow.model.StreamModel) new DslModelParser().parseFromResource(resource);
    }
}
