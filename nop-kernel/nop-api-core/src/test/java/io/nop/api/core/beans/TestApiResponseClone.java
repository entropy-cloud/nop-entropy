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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestApiResponseClone {

    @Test
    public void testCloneInstanceKeepsBizFatal() {
        ApiResponse<String> response = ApiResponse.success("data");
        response.setBizFatal(true);
        // bizFatal影响isBizSuccess/前端提示语义，克隆体（常作为对外序列化对象）不能丢失
        assertEquals(Boolean.TRUE, response.cloneInstance(false).getBizFatal(),
                "clone must keep bizFatal");
    }

    @Test
    public void testCloneInstanceKeepsTryResponse() {
        ApiResponse<String> response = ApiResponse.success("data");
        response.setTryResponse("tryResult");
        assertEquals("tryResult", response.cloneInstance(false).getTryResponse(),
                "clone must keep tryResponse (maker-checker tryMethod result)");
    }

    @Test
    public void testCloneInstanceKeepsCoreFields() {
        ApiResponse<String> response = ApiResponse.error(new ErrorBean("nop.err.test"));
        response.setMsgTimeout(1000);
        response.setWrapper(true);
        ApiResponse<String> clone = response.cloneInstance(false);
        assertTrue(clone.isWrapper());
        assertEquals(1000, clone.getMsgTimeout());
        assertTrue(!clone.isOk());
    }
}
