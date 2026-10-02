/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * WI21 (§八 1): persistent state's operator identity must be STABLE across
 * independent rebuilds of the same declaration — checkpoint recovery matches
 * vertices by the stable (name+occurrence)-derived ids, and operator state keys
 * ({@code operator-{i}}) index into per-vertex snapshots. Two independent
 * {@code buildJobGraph()} passes over the same DSL (a union topology plus a
 * semantic-named join vertex) must produce identical vertex id sets, identical
 * names (union's constant name disambiguated by occurrence, join's semantic
 * {@code "Join:"+id}), and an identical stream-model fingerprint.
 *
 * <p>Two builds, NO execute — the local runner's one-execute-per-JVM limitation
 * (plan 13) does not apply to the public build path, and build-level identity is
 * exactly what the recovery matching consumes.
 */
public class TestPersistentStateOperatorIdStability {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/wi21-stability.beans.xml"));
        container = builder.build("wi21-operator-id-stability");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    private static StreamModel parse(String path) throws Exception {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }

    private static Map<String, String> vertexIdToName(JobGraph graph) {
        Map<String, String> byId = new TreeMap<>();
        for (JobVertex v : graph.getVertices().values()) {
            byId.put(v.getId(), v.getName());
        }
        return byId;
    }

    @Test
    public void twoIndependentBuildsProduceIdenticalOperatorIdentity() throws Exception {
        StreamModel model = parse("/nop/stream/test/wi21-stability.stream.xml");

        // build 1 and build 2: fully independent passes over the same declaration
        JobGraph g1 = StreamModelDslBuilder.of(model).build().buildJobGraph("wi21-stability");
        StreamModel model2 = parse("/nop/stream/test/wi21-stability.stream.xml");
        JobGraph g2 = StreamModelDslBuilder.of(model2).build().buildJobGraph("wi21-stability");

        Map<String, String> ids1 = vertexIdToName(g1);
        Map<String, String> ids2 = vertexIdToName(g2);
        assertEquals(ids1, ids2, "vertex ids and names must be rebuild-stable: " + ids1 + " vs " + ids2);

        // semantic join naming survives chained vertex names: buildJoin's
        // "Join:"+id appears inside the chained operator name
        // ("KeyBy -> Join:j -> Sink")
        boolean joinSemanticNameSurvives = ids1.values().stream().anyMatch(n -> n.contains("Join:j"));
        assertTrue(joinSemanticNameSurvives,
                "join semantic name must survive vertex chaining: " + ids1.values());
        // union vertex occurrence: buildJoin's internal union materializes under
        // the constant "Union" name (DataStreamImpl) — present and rebuild-stable
        // (ids1 == ids2 above already pins cross-build identity)
        assertTrue(ids1.values().stream().anyMatch(n -> n.contains("Union")),
                "the join's internal union vertex must appear under the constant Union name: "
                        + ids1.values());

        // stream-model fingerprint equality — the strongest single identity pin
        // (fingerprints feed the recovery validation)
        StreamModelFingerprint fp1 = g1.getStreamModel().computeFingerprint();
        StreamModelFingerprint fp2 = g2.getStreamModel().computeFingerprint();
        assertEquals(fp1, fp2, "model fingerprints must be rebuild-stable");
    }

    private static void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }
}
