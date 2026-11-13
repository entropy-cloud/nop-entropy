package io.nop.quarkus.web.utils;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C6-007（plan 2524，future 族第 5 例宿主侧，MA-02）：withRoutingContext 内 task.get()
 * 同步异常必须终结返回 future（R21 族纪律）。
 *
 * <p>证据形态：源码级结构钉（2522 R2 Major-3 / 2523 红④⑤ 先例——行为级探针经多形态
 * 实证不可判别：同步 IContext stub 下异常同步传播两态均终结、异步 Vertx 派发下缺陷态
 * 异常被 Vertx context 异常处理器吞掉两态均超时，均无法区分缺陷与修复）。</p>
 * <p>红证 = 缺陷基线（HEAD 原始代码）无 catch Throwable → 结构钉红（try 块缺位）。</p>
 */
public class TestQuarkusExecutorHelperTermination_MA02 {

    @Test
    public void withRoutingContextMustCatchThrowableAndTerminate() throws Exception {
        Path source = Path.of(
                "src/main/java/io/nop/quarkus/web/utils/QuarkusExecutorHelper.java");
        String code = Files.readString(source);
        // 结构钉 1：task.get() 必须包在带 completeExceptionally 的 catch 内
        assertTrue(code.contains("catch (Throwable e)") && code.contains("completeExceptionally"),
                "MA-02: withRoutingContext must catch Throwable from task.get() and"
                        + " completeExceptionally the returned future (defect: exception escapes"
                        + " the runOnContext lambda into the platform swallow channel — caller hangs)");
        // 结构钉 2：catch 必须在 runOnContext lambda 内（非外层——外层 catch 不到 lambda 内异常）
        int runOnContextIdx = code.indexOf("context.runOnContext(");
        int catchIdx = code.indexOf("catch (Throwable e)");
        assertTrue(runOnContextIdx > 0 && catchIdx > runOnContextIdx,
                "MA-02: the catch block must be inside the runOnContext lambda");
    }

    @Test
    public void vertxInstanceAvailableForIntegrationTest() {
        // 烟雾钉：Vertx 可创建（集成测试前提面）
        Vertx vertx = Vertx.vertx();
        vertx.close();
    }
}
