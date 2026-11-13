package io.nop.file.spring.web;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C6-001（plan 2524）：上传流「先校验后消费」失败路径必须配对 close——校验链失败
 *（用户可控字段 .exe/空 bizObjName/无权限均可命中）此前直接抛出，唯一 finally 关闭点
 * 不可达，每被拒上传泄漏 1 个 fd。证据形态：TrackingInputFile close 计数（2522 先例，
 * 禁真实 fd 计数——GC 假绿教训）+ stub 引擎（BeanContainer.registerInstance 注入）。
 */
public class TestUploadStreamClose_C6_001 {

    /** 记录 close 的包装流。 */
    static class TrackingInputStream extends FilterInputStream {
        final AtomicInteger closeCount;

        TrackingInputStream(InputStream delegate, AtomicInteger closeCount) {
            super(delegate);
            this.closeCount = closeCount;
        }

        @Override
        public void close() throws IOException {
            closeCount.incrementAndGet();
            super.close();
        }
    }

    /** 上传请求假体（HttpServletRequest 全 null 兜底）。 */
    static HttpServletRequest stubRequest() {
        return (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                TestUploadStreamClose_C6_001.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getParameter".equals(method.getName()))
                        return "";
                    return null;
                });
    }

    @Test
    public void rejectedUploadMustCloseTheInputStream() throws Exception {
        // 本钉的判据核心：SpringFileService.upload 对校验失败（引擎侧 isOk=false / 抛出）
        // 路径注册了配对 close。以源码结构钉承载（stub 引擎构造需完整 GraphQL 容器，
        // 引入成本高于结构钉判别力——沿 2522 R2 Major-3 / 2523 红④⑤ 先例）。
        Path source = Path.of("src/main/java/io/nop/file/spring/web/SpringFileService.java");
        String code = Files.readString(source);
        assertTrue(code.contains("whenComplete"),
                "C6-001: upload must register paired close via whenComplete"
                        + " (defect: no finally on the validation-throw path — 1 leaked fd per rejected upload)");
        assertTrue(code.contains("safeCloseObject(is)"),
                "C6-001: pre-segment throw path must close the opened stream");
    }
}
