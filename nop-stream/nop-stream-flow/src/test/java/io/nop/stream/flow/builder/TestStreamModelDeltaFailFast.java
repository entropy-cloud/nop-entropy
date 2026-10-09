/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 51 — Fail-fast preservation under Delta customization.
 *
 * <p>Verifies that a Delta which introduces an unsupported top-level registry
 * (e.g. {@code <streams>}) still triggers the Stage 50 fail-fast in
 * {@link StreamModelDslBuilder}. The Delta merge machinery must NOT silently
 * bypass the builder's fail-fast checks — the merged model is subject to the
 * same constraints as a non-delta model.
 */
public class TestStreamModelDeltaFailFast {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void deltaAddingJavaOnlyRegistryStillFailsFast() {
        // WI12 (plan 2358): the delta carrier moved from <streams> (removed from the
        // schema) to <coders> — the pinned Java-only registry whose fail-fast must
        // still fire after the delta merge (the delta must NOT silently bypass it).
        StreamModel model = parseStreamXml("/nop/stream/test/test-delta-failfast-extends.stream.xml");

        assertTrue(model.hasCoders(),
                "Delta-merged model must contain the delta-introduced <coders> registry");

        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.not-implemented", ex.getErrorCode().toString(),
                () -> ex.getMessage());
        assertTrue(ex.getMessage().contains("<coders>"),
                "Fail-fast must reject <coders> even under delta merge: " + ex.getMessage());
    }

    private static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }
}
