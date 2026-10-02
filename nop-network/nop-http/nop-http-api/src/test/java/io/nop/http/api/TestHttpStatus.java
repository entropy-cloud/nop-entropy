package io.nop.http.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 HttpStatus 分类函数的边界语义：信息/成功/重定向/客户端错误/服务端错误
 * 按标准 HTTP 状态码区间划分，isError 覆盖 4xx+5xx。
 */
public class TestHttpStatus {

    @Test
    public void testInformationalBoundaries() {
        assertTrue(HttpStatus.isInformational(100));
        assertTrue(HttpStatus.isInformational(199));
        assertFalse(HttpStatus.isInformational(200));
        assertFalse(HttpStatus.isInformational(99));
    }

    @Test
    public void testSuccessBoundaries() {
        assertTrue(HttpStatus.isSuccess(200));
        assertTrue(HttpStatus.isSuccess(299));
        assertFalse(HttpStatus.isSuccess(300));
        assertFalse(HttpStatus.isSuccess(199));
    }

    @Test
    public void testRedirectionBoundaries() {
        assertTrue(HttpStatus.isRedirection(301));
        assertTrue(HttpStatus.isRedirection(399));
        assertFalse(HttpStatus.isRedirection(400));
    }

    @Test
    public void testClientErrorBoundaries() {
        assertTrue(HttpStatus.isClientError(400));
        assertTrue(HttpStatus.isClientError(404));
        assertTrue(HttpStatus.isClientError(499));
        assertFalse(HttpStatus.isClientError(500));
    }

    @Test
    public void testServerErrorBoundaries() {
        assertTrue(HttpStatus.isServerError(500));
        assertTrue(HttpStatus.isServerError(503));
        assertFalse(HttpStatus.isServerError(600));
        assertFalse(HttpStatus.isServerError(499));
    }

    @Test
    public void testErrorCoversClientAndServer() {
        assertTrue(HttpStatus.isError(400));
        assertTrue(HttpStatus.isError(599));
        assertFalse(HttpStatus.isError(399));
        assertFalse(HttpStatus.isError(200));
    }
}
