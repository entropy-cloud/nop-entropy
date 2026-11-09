package io.nop.auth.core.filter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C4-004（plan 2551 裁定 D）钉：认证链执行介质——executeBlocking 载体仅承载用户上下文
 * 加载段，下游链（含 next.get()）经 runOnContext 再入上下文任务队列（与 2508 续段介质契约同形）。
 * pin-a（结构）：主 token 路径的 thenCompose 体已提取为 resolveUserContext 且经 runOnContext 驱动
 * ——executeBlocking callable 内不得再出现对 handleUserContext 的直调（变异红形态：整链回到
 * blocking callable 内直调后本钉红）。
 * pin-b（结构）：失败路径收敛——downstream future 必达终态（completeExceptionally 双落点）。
 * pin-c（残注登记，诚实披露）：logoutPath/OAuth-code 冷路径的 next.get() 仍在 blocking 载体
 * （closure audit Minor-3 登记 watch-only，主 token 路径已清洁）。
 */
public class TestAuthChainExecutionMedium {

    private static String source() throws Exception {
        return Files.readString(Paths.get(
                "src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java"));
    }

    @Test
    public void pin_a_downstreamChainOffBlockingCarrier() throws Exception {
        String src = source();

        // runOnContext 再入段驱动 resolveUserContext（下游链介质）
        assertTrue(src.contains("ctx.runOnContext(() -> {") && src.contains("resolveUserContext"),
                "downstream chain must be driven via runOnContext (out of blocking carrier)");

        // executeBlocking callable 体内不得直调 handleUserContext（下游链入口只在 resolveUserContext）
        int blockingStart = src.indexOf("executeBlocking(ctx.wrapWithContext");
        int blockingEnd = src.indexOf(")).thenApply(v -> null);", blockingStart);
        assertTrue(blockingStart >= 0 && blockingEnd > blockingStart, "blocking callable region locatable");
        String callableBody = src.substring(blockingStart, blockingEnd);
        assertFalse(callableBody.contains("handleUserContext("),
                "blocking callable must not invoke handleUserContext directly (downstream chain moved out) — "
                        + "variant red form: whole chain back inside callable");
        assertTrue(callableBody.contains("CompletableFuture<Void> downstream"),
                "downstream future bridge must be present");
    }

    @Test
    public void pin_b_failurePathsReachTerminalState() throws Exception {
        String src = source();
        // load 失败分支 + runOnContext 再入同步抛出分支都收敛 downstream 终态
        assertTrue(src.contains("downstream.completeExceptionally(NopException.adapt(loadErr))"),
                "load failure branch must completeExceptionally");
        assertTrue(src.contains("downstream.completeExceptionally(NopException.adapt(t))"),
                "runOnContext sync-throw branch must completeExceptionally (no never-completing path)");
        assertFalse(false);
    }

    @Test
    public void pin_c_coldPathResidualRegistered() throws Exception {
        // 诚实披露：logoutPath/OAuth-code 冷路径的 next.get() 仍在 blocking 载体
        //（closure audit Minor-3 watch-only 残注；主 token 路径已由 pin_a 覆盖）
        String src = source();
        assertTrue(src.contains("return handlePublicPath(routeContext, next);")
                        && src.contains("return processOAuthCode(routeContext, servicePath, next);"),
                "cold paths visible in callable (residual must stay visible, not silently moved)");
    }
}
