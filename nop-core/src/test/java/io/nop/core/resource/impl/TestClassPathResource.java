/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.impl;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.nop.core.resource.IResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestClassPathResource {
    @Test
    public void testNormalizePath() {
        String path = ClassPathResource.normalizeClassPath("classpath:/_vfs/a.txt");
        assertEquals("classpath:_vfs/a.txt", path);
    }

    @Test
    public void testLength() {
        IResource resource = new ClassPathResource("classpath:" + CacheLoader.class.getName().replace('.', '/') + ".class");
        assertTrue(resource.exists());
        assertTrue(resource.length() > 0);
        assertTrue(resource.lastModified() > 0);
    }
}
