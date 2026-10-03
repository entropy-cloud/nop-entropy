/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.List;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.testing.CollectingSinkFunction;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI11: continuous (windowless) GROUP BY under the D1=(a) adjudication — keyBy +
 * reduce with LAST-VALUE-WINS final-value semantics. The discriminating assertion:
 * the sink observes the INTERMEDIATE reduction values per key (e.g. key 2 sums as
 * 2, 4, 6 — not just the final 6), which is exactly the observable proof that the
 * stream is NOT append-only (an append-only window aggregate would emit only the
 * final value) and NOT a retract stream (no retractions are emitted).
 *
 * <p>Branch reconciliation: (b) retract/upsert route stays unimplemented — the D10
 * spec-only fail-fast for ACCUMULATING_AND_RETRACTING remains green and
 * UPSERT_BY_KEY has zero call sites; §八 7 — the reduce topology's capability
 * declaration does not claim STRICT_EXACTLY_ONCE.
 */
public class TestContinuousGroupBy {

    private static IBeanContainerImplementor container;

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource(
                "classpath:_vfs/nop/stream/test/test-reduce-pipeline.beans.xml"));
        container = builder.build("stream-flow-continuous-groupby");
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

    @Test
    public void continuousGroupByEmitsIntermediateReductionValues() throws Exception {
        @SuppressWarnings("unchecked")
        CollectingSinkFunction<Integer> sink = (CollectingSinkFunction<Integer>)
                BeanContainer.instance().getBean("advancedCollectingSink");
        sink.clear();

        // source [1,1,2,2,2] → keyBy(value) → reduce(sum) → sink
        StreamModel model = parseStreamXml("/nop/stream/test/test-reduce-pipeline.stream.xml");
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        env.execute("wi11-continuous-groupby");

        List<Integer> collected = sink.getCollected();

        // LAST-VALUE-WINS evidence: the sink sees EVERY intermediate reduction value.
        // key 1: [1, 1] → emits 1, 2   key 2: [2, 2, 2] → emits 2, 4, 6
        // An append-only window form would emit only [2, 6]; a retract form would
        // carry retraction markers — neither matches this sequence.
        assertEquals(java.util.Arrays.asList(1, 2, 2, 4, 6), collected,
                "continuous GROUP BY must emit every intermediate reduction value (last-value-wins)");
        assertTrue(collected.size() == 5, "5 inputs → 5 emissions (one per input element)");
    }

    @Test
    public void retractRouteStaysUnimplemented() {
        // (b)-route reconciliation: UPSERT_BY_KEY's only production occurrence is its
        // enum declaration — zero call sites, the (b) upsert route was never wired.
        // The scan is non-vacuous: the declaration itself lives inside the scanned
        // roots (count would be 1 if nothing else), so a broken scan path or an
        // accidental call site both fail loudly.
        assertEquals(1, countReferences("UPSERT_BY_KEY",
                "nop-stream/nop-stream-core/src/main/java",
                "nop-stream/nop-stream-flow/src/main/java",
                "nop-stream/nop-stream-sql/src/main/java"),
                "(b) route: UPSERT_BY_KEY must appear only as its enum declaration (no call sites)");
    }

    /**
     * §八 7 reconciliation: the reduce topology's declared processing guarantee must
     * NOT be STRICT_EXACTLY_ONCE — reduce's output is last-value-wins (not a strict
     * commit surface). The builder's createTestEnvironment hardcodes AT_LEAST_ONCE;
     * if the factory switches to STRICT, or the pipeline declares a
     * {@code <checkpoint processingGuarantee="STRICT_EXACTLY_ONCE">} element, the
     * assertion fails.
     */
    @Test
    public void reduceTopologyDoesNotClaimStrictGuarantee() throws Exception {
        StreamModel model = parseStreamXml("/nop/stream/test/test-reduce-pipeline.stream.xml");
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model).build();
        // the reduce branch must not have escalated the guarantee to STRICT
        org.junit.jupiter.api.Assertions.assertNotEquals(
                io.nop.stream.core.checkpoint.ProcessingGuarantee.STRICT_EXACTLY_ONCE,
                env.getCheckpointConfig().getProcessingGuarantee(),
                "continuous GROUP BY (last-value-wins output) must not claim STRICT_EXACTLY_ONCE (§八 7)");
    }

    private static int countReferences(String symbol, String... roots) {
        int count = 0;
        // surefire's working dir is the module basedir — walk up to the repo root so
        // the repo-relative scan roots actually resolve (a silent skip would make
        // this assertion vacuously green)
        java.nio.file.Path repoRoot = java.nio.file.Paths.get(System.getProperty("user.dir"))
                .toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("ai-dev/backlog/nop-stream-sql-roadmap.md"))) {
            repoRoot = repoRoot.getParent();
        }
        org.junit.jupiter.api.Assertions.assertNotNull(repoRoot,
                "repo root must be locable from the working directory");
        for (String rootPath : roots) {
            java.io.File root = repoRoot.resolve(rootPath).toFile();
            if (!root.exists()) {
                org.junit.jupiter.api.Assertions.fail(
                        "scan root must exist (non-vacuous assertion): " + rootPath);
            }
            java.util.Deque<java.io.File> stack = new java.util.ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                java.io.File f = stack.pop();
                if (f.isDirectory()) {
                    java.io.File[] children = f.listFiles();
                    if (children != null) {
                        for (java.io.File c : children) {
                            stack.push(c);
                        }
                    }
                } else if (f.getName().endsWith(".java")) {
                    try {
                        String text = java.nio.file.Files.readString(f.toPath());
                        int idx = 0;
                        while ((idx = text.indexOf(symbol, idx)) >= 0) {
                            count++;
                            idx += symbol.length();
                        }
                    } catch (java.io.IOException e) {
                        // skip unreadable
                    }
                }
            }
        }
        return count;
    }

    private static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }
}
