/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.core.resource;

import io.nop.commons.util.StringHelper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPathPattern {
    @Test
    public void testPathPattern() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:org/springframework/aot/*.class");
        System.out.println(StringHelper.join(Arrays.asList(resources), "\n"));
        assertTrue(resources.length > 0);
    }
}
