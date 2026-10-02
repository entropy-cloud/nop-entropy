package io.nop.tcc.core.meta;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.annotations.txn.TccMethod;
import io.nop.api.core.annotations.txn.TccTransactional;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiHeaders;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 TCC 服务元数据语义：反射构建的 method 元数据命名（bizObj__method，Async 后缀剥离）、
 * txnGroup 来自 TccTransactional 注解、requireMethod 对未知方法报错、
 * DefaultTccServiceMetaLoader 在无注册元数据时回退解析 request 头。
 */
public class TestTccServiceMetaReflection {

    @BizObjName("DemoSvc")
    public interface DemoTccService {
        @TccMethod(confirmMethod = "confirmDo", cancelMethod = "cancelDo")
        @TccTransactional(txnGroup = "demo-group")
        String doSomething(String arg);

        @TccMethod(confirmMethod = "confirmQuery", cancelMethod = "cancelQuery")
        CompletableFuture<String> queryAsync(String arg);

        String plainMethod(String arg);
    }

    @Test
    public void testBuildMethodMetaFromAnnotations() {
        TccServiceMeta meta = ReflectionTccServiceMetaBuilder.INSTANCE.build("demo", DemoTccService.class);

        TccMethodMeta doMeta = meta.getMethod("DemoSvc__doSomething");
        assertNotNull(doMeta, "tcc method must be discovered");
        assertEquals("DemoSvc__confirmDo", doMeta.getConfirmMethod());
        assertEquals("DemoSvc__cancelDo", doMeta.getCancelMethod());
        assertEquals("demo-group", doMeta.getTxnGroup(), "txnGroup must come from TccTransactional");
    }

    @Test
    public void testAsyncSuffixStrippedFromMethodName() {
        TccServiceMeta meta = ReflectionTccServiceMetaBuilder.INSTANCE.build("demo", DemoTccService.class);

        assertNotNull(meta.getMethod("DemoSvc__query"),
                "queryAsync with CompletionStage return must be registered without Async suffix");
        assertNull(meta.getMethod("DemoSvc__queryAsync"));
    }

    @Test
    public void testNonTccMethodNotRegistered() {
        TccServiceMeta meta = ReflectionTccServiceMetaBuilder.INSTANCE.build("demo", DemoTccService.class);
        assertNull(meta.getMethod("DemoSvc__plainMethod"),
                "method without @TccMethod must not be part of tcc meta");
    }

    @Test
    public void testRequireMethodThrowsForUnknown() {
        TccServiceMeta meta = ReflectionTccServiceMetaBuilder.INSTANCE.build("demo", DemoTccService.class);
        assertThrows(NopException.class, () -> meta.requireMethod("DemoSvc__notExists"));
    }

    @Test
    public void testMergeOverridesMethodEntries() {
        TccServiceMeta base = ReflectionTccServiceMetaBuilder.INSTANCE.build("demo", DemoTccService.class);
        TccMethodMeta override = new TccMethodMeta("other-group", "DemoSvc__doSomething",
                "DemoSvc__confirmDo2", "DemoSvc__cancelDo2");

        TccServiceMeta merged = base.merge(java.util.Map.of("DemoSvc__doSomething", override));

        assertEquals("DemoSvc__confirmDo2", merged.requireMethod("DemoSvc__doSomething").getConfirmMethod(),
                "merged entries must win over base meta");
    }

    @Test
    public void testLoaderFallsBackToRequestHeaders() {
        DefaultTccServiceMetaLoader loader = new DefaultTccServiceMetaLoader();
        loader.setGuessTccMethod(false);

        ApiRequest<?> request = new ApiRequest<>();
        assertNull(loader.getMethodMeta("unknown-svc", "someMethod", request),
                "no meta and no headers and guess disabled must yield null");

        ApiHeaders.setTxnGroup(request, "header-group");
        ApiHeaders.setTccCancel(request, "cancelDo");
        TccMethodMeta meta = loader.getMethodMeta("unknown-svc", "doSomething", request);
        assertNotNull(meta, "cancel header must produce a fallback meta");
        assertEquals("header-group", meta.getTxnGroup());
        assertEquals("cancelDo", meta.getCancelMethod());
        assertNull(meta.getConfirmMethod());
    }

    @Test
    public void testLoaderGuessTccMethod() {
        DefaultTccServiceMetaLoader loader = new DefaultTccServiceMetaLoader();
        loader.setGuessTccMethod(true);

        ApiRequest<?> request = new ApiRequest<>();
        TccMethodMeta meta = loader.getMethodMeta("unknown-svc", "doSomething", request);
        assertNotNull(meta, "guess enabled must synthesize cancel method");
        assertEquals("cancelDoSomething", meta.getCancelMethod(), "guess rule: cancel + capitalized method");
    }
}
