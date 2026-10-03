/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.graphql;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGraphQLResponseBean {

    @Test
    public void testNoErrorState() {
        GraphQLResponseBean res = new GraphQLResponseBean();
        res.setData("data");
        assertFalse(res.hasError());
        // 无 extensions 时 status 缺省为 0（成功）
        assertEquals(0, res.getStatus());
        assertNull(res.getErrorCode());
        assertNull(res.getMsg());
        assertNull(res.getBizFatal());
        assertNull(res.toErrorBean());
        assertEquals("data", res.getData());
    }

    @Test
    public void testStatusDefaultsToFailureWhenExtensionsExistWithoutStatus() {
        GraphQLResponseBean res = new GraphQLResponseBean();
        res.setErrors(Arrays.asList(new GraphQLErrorBean()));
        // extensions 为 null 时无论是否报错 status 都是 0
        assertEquals(0, res.getStatus());

        // extensions 非空但缺 nop-status：有错误 → 缺省 -1（失败）
        res.setMsg("err-message");
        assertEquals(-1, res.getStatus());

        // 无错误 → 缺省 0
        GraphQLResponseBean ok = new GraphQLResponseBean();
        ok.setMsg("info");
        assertEquals(0, ok.getStatus());
    }

    @Test
    public void testAddErrorPropagatesCodeStatusAndBizFatal() {
        GraphQLResponseBean res = new GraphQLResponseBean();
        ErrorBean error = new ErrorBean("nop.test.err");
        error.setDescription("业务失败");
        error.setStatus(1);
        error.setBizFatal(true);

        res.addError(error);
        assertTrue(res.hasError());
        assertEquals("nop.test.err", res.getErrorCode());
        assertEquals(1, res.getStatus());
        assertEquals(Boolean.TRUE, res.getBizFatal());
        assertEquals("业务失败", res.getMsg());
        assertEquals(1, res.getErrors().size());
        // addError 无 sourceLocation 时不生成 locations
        assertNull(res.getErrors().get(0).getLocations());
    }

    @Test
    public void testAddErrorWithoutCodeKeepsNullCodeButKeepsMessage() {
        GraphQLResponseBean res = new GraphQLResponseBean();
        ErrorBean error = new ErrorBean();
        error.setDescription("仅描述");
        res.addError(error);
        // addError 对 null errorCode 是"移除扩展"语义，响应上的错误码保持为 null
        assertNull(res.getErrorCode());
        // 首条错误 message 仍作为 msg 返回；错误码的兜底回填发生在 toErrorBean 中
        assertEquals("仅描述", res.getMsg());
    }

    /**
     * 回归覆盖 wi2#1（plan 2306 项 12）：toErrorBean() 对未设置 nop-biz-fatal 扩展的
     * 错误响应必须 null 安全（bizFatal 缺省为 false），不得因 Boolean 拆箱抛 NPE。
     */
    @Test
    public void testToErrorBeanWithoutBizFatalExtensionShouldNotThrow() {
        GraphQLResponseBean res = new GraphQLResponseBean();
        ErrorBean error = new ErrorBean("nop.err.demo");
        error.setDescription("desc");
        res.addError(error);
        // 修复后应返回 ErrorBean 而不是抛 NPE
        ErrorBean converted = res.toErrorBean();
        org.junit.jupiter.api.Assertions.assertNotNull(converted);
        org.junit.jupiter.api.Assertions.assertFalse(converted.isBizFatal(),
                "未设置扩展时 bizFatal 必须缺省为 false");
    }

    @Test
    public void testToErrorBeanPrefersErrorCodeAndBizFatal() {
        GraphQLResponseBean res = new GraphQLResponseBean();
        ErrorBean error = new ErrorBean("nop.test.err");
        error.setDescription("desc");
        error.setBizFatal(true);
        res.addError(error);

        ErrorBean converted = res.toErrorBean();
        assertEquals("nop.test.err", converted.getErrorCode());
        assertEquals("desc", converted.getDescription());
        assertTrue(converted.isBizFatal());
    }

    @Test
    public void testToApiResponseMapping() {
        GraphQLResponseBean res = new GraphQLResponseBean();
        res.setData("payload");
        res.setMsg("ok");
        res.setStatus(0);

        ApiResponse<Object> api = res.toApiResponse();
        assertEquals("payload", api.getData());
        assertEquals(0, api.getStatus());
        assertEquals("ok", api.getMsg());
        assertTrue(api.isWrapper());
    }

    @Test
    public void testExtensionRemoveOnNullValue() {
        GraphQLResponseBean res = new GraphQLResponseBean();
        res.setMsg("hello");
        assertEquals("hello", res.getMsg());
        // setExtension(null) 语义为移除
        res.setMsg(null);
        assertNull(res.getMsg());
        assertNull(res.getExtension("nop-msg"));

        res.setStatus(5);
        assertEquals(5, res.getStatus());
        res.setStatus(0);
        assertEquals(0, res.getStatus());
    }
}
