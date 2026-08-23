/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestApiRequest {

    @Test
    public void testCloneInstanceCopiesProperties() {
        ApiRequest<String> request = ApiRequest.build("data");
        request.setProperty("k1", "v1");
        request.setProperty("k2", 2);

        ApiRequest<String> clone = request.cloneInstance();
        // 克隆体必须保留全部properties
        assertEquals("v1", clone.getStringProperty("k1"));
        assertEquals("2", clone.getStringProperty("k2"));
        assertNotNull(clone.getProperties());
        assertEquals(2, clone.getProperties().size());

        // 克隆体的properties是拷贝，修改不影响源对象
        clone.setProperty("k3", "v3");
        assertNull(request.getProperty("k3"));

        // 源对象的properties保持不变
        assertEquals("v1", request.getStringProperty("k1"));
        assertEquals(2, request.getProperties().size());
    }

    @Test
    public void testCloneInstanceNoProperties() {
        ApiRequest<String> request = ApiRequest.build("data");
        ApiRequest<String> clone = request.cloneInstance();
        assertNull(clone.getProperties());
        assertEquals("data", clone.getData());
        assertTrue(clone == request.cloneInstance(true) || clone.getData().equals("data"));
    }
}
