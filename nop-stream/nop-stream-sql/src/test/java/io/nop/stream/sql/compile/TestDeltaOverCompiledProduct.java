/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.sql.compile.testing.SqlTestSink;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI19: the StreamSqlCompiler PRODUCT (a compiled stream model) is Delta-
 * customizable, and the customization is observable at execution.
 *
 * <p>§八 10 single conclusion: Delta modifies the MODEL, never a runtime object.
 * Evidence shape: the committed compiled product is a plain xdef model resource;
 * the delta is a second model resource with {@code x:extends} pointing at it;
 * the merged model carries the delta-added filter and rewired edges (structure
 * assertions), and a delta execution proves the added filter actually runs over
 * the compiled topology (item "b" rows absent from the sink).
 *
 * <p>Anti-stale pin: the committed product must equal a fresh
 * {@code StreamSqlCompiler.compile} output with the same deterministic schema
 * (LinkedHashMap) — if the compiler evolves, this test goes red and forces
 * regeneration of the resource (no silently stale fixture).
 *
 * <p>One execute per test class (local runner limit): only the DELTA model is
 * executed — its output (b rows absent, all other rows intact) proves both that
 * the compiled topology runs and that the delta customization took effect; the
 * un-customized product's execution is already pinned by the WI17 E2Es.
 */
public class TestDeltaOverCompiledProduct {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/sql/test/sql-compile.beans.xml"));
        container = builder.build("wi19-delta-compiled");
        container.start();
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        if (container != null)
            container.stop();
        CoreInitialization.destroy();
        BeanContainer.registerInstance(null);
    }

    private static Map<String, String> schema() {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("item", "string");
        schema.put("amount", "int");
        return schema;
    }

    @Test
    public void committedProductMatchesFreshCompile() {
        String fresh = StreamSqlCompiler.compile(null,
                "SELECT item, amount FROM orders WHERE amount > 0",
                schema(), "testSink");
        String committed = io.nop.core.resource.VirtualFileSystem.instance()
                .getResource("/nop/stream/sql/test/wi19-compiled-base.stream.xml").readText();
        assertEquals(normalize(committed), normalize(fresh),
                "the committed compiled product must match a fresh compile (anti-stale pin) — "
                        + "if the compiler changed, regenerate the resource");
    }

    @Test
    public void mergedModelCarriesTheDeltaTopology() throws Exception {
        StreamModel delta = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/sql/test/wi19-compiled-delta.stream.xml"));

        // the delta-added filter is in the merged transform set
        boolean dropBPresent = delta.getTransforms().stream()
                .anyMatch(t -> "wi19DropB".equals(t.getId()));
        assertTrue(dropBPresent, "merged model must contain the delta-added wi19DropB filter");
        // the compiled product's own transforms survive the merge
        for (String compiledId : new String[]{"sflt", "ssrc", "sproj", "out"}) {
            boolean present = delta.getTransforms().stream()
                    .anyMatch(t -> compiledId.equals(t.getId()));
            assertTrue(present, "compiled transform '" + compiledId + "' must survive the merge");
        }
        // edge rewiring: e_out now routes sproj -> wi19DropB, and wi19DropB -> out
        boolean eOutRedirected = delta.getEdges().stream()
                .anyMatch(e -> "e_out".equals(e.getId()) && "sproj".equals(e.getFrom())
                        && "wi19DropB".equals(e.getTo()));
        boolean newEdge = delta.getEdges().stream()
                .anyMatch(e -> "e_wi19".equals(e.getId()) && "wi19DropB".equals(e.getFrom())
                        && "out".equals(e.getTo()));
        assertTrue(eOutRedirected && newEdge, "delta must rewire e_out and add wi19DropB -> out");
        // the <sql> declaration was consumed by the WI17 compiler before the merge
        assertEquals(null, delta.getSql(),
                "a compiled-product base carries no <sql> element");
    }

    @Test
    public void deltaExecutionRunsTheAddedFilterOverTheCompiledTopology() throws Exception {
        StreamModel delta = (StreamModel) new DslModelParser().parseFromResource(
                new ClassPathResource("classpath:_vfs/nop/stream/sql/test/wi19-compiled-delta.stream.xml"));
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(delta).build();
        env.execute("wi19-delta");

        SqlTestSink sink = (SqlTestSink) BeanContainer.instance().getBean("testSink");
        List<String> rows = sink.getCollected().stream()
                .map(o -> {
                    Map<?, ?> m = (Map<?, ?>) o;
                    return m.get("item") + "=" + m.get("amount");
                })
                .sorted()
                .collect(Collectors.toList());
        // the compiled product's WHERE (amount > 0) ran, AND the delta-added
        // drop-"b" filter ran on top — b rows absent, every other row intact
        assertEquals(Arrays.asList("a=1", "a=3", "c=1"), rows,
                () -> "delta execution must run the added filter over the compiled topology: " + rows);
    }

    private static String normalize(String xml) {
        // strip the committed resource's provenance comment header, then whitespace
        return xml.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ").trim();
    }
}
