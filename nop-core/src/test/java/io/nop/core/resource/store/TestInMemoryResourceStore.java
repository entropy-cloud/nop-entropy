/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.store;

import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestInMemoryResourceStore extends BaseTestCase {
    @Test
    public void testZipStore() throws Exception {
        IResource resource = testResource("test.zip");
        ZipResourceStore store = ZipResourceStore.build(resource.toFile(), "/", "_vfs");
        List<? extends IResource> children = store.getChildren("/test");
        assertEquals(2, children.size());
        assertEquals("/test/sub", children.get(0).getPath());
        store.close();
    }
}
