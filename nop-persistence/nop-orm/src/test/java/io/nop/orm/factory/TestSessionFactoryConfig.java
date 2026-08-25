/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.factory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSessionFactoryConfig {

    /**
     * 未先setInterceptors时直接调用addInterceptor不应抛UnsupportedOperationException。
     * 历史版本默认值是Collections.emptyList()（不可变）
     */
    @Test
    public void testAddInterceptorOnDefaultList() {
        SessionFactoryConfig config = new SessionFactoryConfig();
        config.addInterceptor(null);
        config.addInterceptor(null);
        assertEquals(2, config.getInterceptors().size());

        config.removeInterceptor(null);
        assertEquals(1, config.getInterceptors().size());
    }
}
