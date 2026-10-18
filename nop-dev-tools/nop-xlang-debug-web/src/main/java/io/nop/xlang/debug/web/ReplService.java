/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.json.JsonTool;
import io.nop.xlang.XLangConfigs;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.debugger.session.DebugSessionHandle;
import io.nop.xlang.debugger.session.DebugSessionScope;
import io.nop.xlang.debugger.session.ReplCancelledError;
import io.nop.xlang.debugger.session.ThreadLocalReplDeadlineChannel;
import io.nop.xlang.debugger.session.XLangDebuggerConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import static io.nop.xlang.debugger.session.XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_REPL_COMPILE_TIMEOUT;
import static io.nop.xlang.debugger.session.XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_REPL_EXECUTE_TIMEOUT;
import static io.nop.xlang.debugger.session.XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_REPL_IMPORT_WHITELIST;
import static io.nop.xlang.debugger.session.XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_REPL_MAX_CONCURRENT;
import static io.nop.xlang.debugger.session.XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_REPL_MAX_SOURCE_BYTES;

/**
 * 在线 eval REPL 服务端（D6 §3.1/§3.3/§3.4/§3.5，plan 2473 Phase 2）：compile 诊断
 * 与 compile+execute 两端点的容器无关核心。取消 = ThreadLocalReplDeadlineChannel
 * deadline 窗口（融合执行器每入口检查）；沙箱 = 编译面词法校验（import 默认拒绝 /
 * Promise-async 禁用 / __repl* 保留标识符——词法近似边界见 D6 §3.3 与 plan 声明）；
 * 限制表衔接 D6 §3.4（execute cap 120s / compile cap 30s / source 256KB / 结果 1MB
 * 截断 / 全局并发 + 同会话串行限流）；审计 = 专用 logger 结构化单行
 * （consumer 可注入供测试）。
 */
public class ReplService {
    static final Logger LOG = LoggerFactory.getLogger(ReplService.class);
    /** D6 §3.5：审计专用 logger（接入平台日志采集；脚本行为不可影响）。 */
    static final Logger AUDIT_LOG = LoggerFactory.getLogger("nop.xlang.debug.audit");

    public static final int MAX_EXECUTE_TIMEOUT_MS = 120_000;
    public static final int MAX_COMPILE_TIMEOUT_MS = 30_000;
    public static final int MAX_RESULT_BYTES = 1024 * 1024;
    private static final int SOURCE_PREVIEW_LEN = 200;

    private final DebugGatewayRuntime runtime;
    private final ReplAccessGuard guard;
    private final ReplSourceValidator sandboxValidator = new ReplSourceValidator();
    private final Semaphore globalPermits;
    private final Set<String> inFlightSessions = ConcurrentHashMap.newKeySet();
    /** 审计事件消费口（生产 = 结构化日志；测试注入捕获）。 */
    private volatile Consumer<Map<String, Object>> auditConsumer;
    /** 执行期谓词（D6 §一.5 运行层门控；默认读配置，测试可注入）。 */
    private volatile java.util.function.BooleanSupplier enabledPredicate = XLangConfigs.CFG_XLANG_DEBUGGER_ENABLED::get;

    public ReplService(DebugGatewayRuntime runtime, ReplAccessGuard guard) {
        this.runtime = runtime;
        this.guard = guard;
        int maxConcurrent = (int) read(XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_REPL_MAX_CONCURRENT);
        this.globalPermits = new Semaphore(Math.max(1, maxConcurrent));
    }

    public void setAuditConsumer(Consumer<Map<String, Object>> consumer) {
        this.auditConsumer = consumer;
    }

    /** 测试注入点：运行期门控谓词。 */
    public void setEnabledPredicate(java.util.function.BooleanSupplier predicate) {
        this.enabledPredicate = predicate;
    }

    // ===== compile =====

    /** D6 §3.1 compile 端点：门控序（登录/权限/尺寸/沙箱）→ compile deadline → 诊断映射。 */
    public Map<String, Object> compile(String sourcePath, String source, Map<String, Object> headers) {
        long start = System.currentTimeMillis();
        IUserContext ctx = guard.checkAccess(headers, ReplAccessGuard.PERM_COMPILE);
        checkSourceSize(source);

        String actor = ctx.getUserId();
        long deadline = System.currentTimeMillis()
                + Math.min(read(XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_REPL_COMPILE_TIMEOUT),
                        MAX_COMPILE_TIMEOUT_MS);
        Map<String, Object> result;
        String auditResult;
        ThreadLocalReplDeadlineChannel channel = ThreadLocalReplDeadlineChannel.instance();
        channel.begin(deadline);
        try {
            List<Map<String, Object>> diagnostics = new ArrayList<>();
            try {
                // 沙箱校验先于 compileFullExpr（xlib import 的编译期宏执行发生在 compile
                // 阶段）；语法错误落诊断、白名单违规以 400 抛出（卫语句防被下方
                // catch(Exception) 吞成诊断——plan 2478 Phase 1 放置约束）
                SourceLocation loc = sourcePath == null ? null : SourceLocation.fromLine(sourcePath, 1);
                validateSandbox(source, loc);
                XLang.newCompileTool().compileFullExpr(loc, source);
            } catch (ReplAccessException e) {
                throw e;
            } catch (NopException e) {
                Map<String, Object> diagnostic = new LinkedHashMap<>();
                SourceLocation errLoc = e.getErrorLocation();
                diagnostic.put("line", errLoc == null || errLoc.getLine() <= 0 ? 1 : errLoc.getLine());
                diagnostic.put("severity", "error");
                diagnostic.put("message", e.getDescription() != null ? e.getDescription() : e.getErrorCode());
                if (e.getErrorCode() != null)
                    diagnostic.put("errorCode", e.getErrorCode());
                diagnostics.add(diagnostic);
            } catch (Exception e) {
                LOG.warn("nop.xlang.debug-web.repl-compile-fail", e);
                Map<String, Object> diagnostic = new LinkedHashMap<>();
                diagnostic.put("line", 1);
                diagnostic.put("severity", "error");
                diagnostic.put("message", "compile failed: " + e.getClass().getSimpleName());
                diagnostics.add(diagnostic);
            }
            result = new LinkedHashMap<>();
            result.put("diagnostics", diagnostics);
            auditResult = "ok";
        } catch (ReplCancelledError e) {
            // 编译期求值死循环被 compile deadline 打断（D6 §3.4：超时非纯防御值）
            result = compileErrorResult("repl compile timeout", e.getErrorCode());
            auditResult = "timeout";
        } finally {
            channel.end();
        }
        audit(actor, null, "compile", source, auditResult, System.currentTimeMillis() - start);
        return result;
    }

    // ===== execute =====

    /** D6 §3.1 execute 端点：全门控序 → deadline 窗口执行 → 结果/取消/超时响应。 */
    public Map<String, Object> execute(String sessionId, String source, Integer timeoutMs,
                                       Map<String, Object> headers) {
        long start = System.currentTimeMillis();
        IUserContext ctx = guard.checkAccess(headers, ReplAccessGuard.PERM_EXECUTE);
        String actor = ctx.getUserId();

        // 会话存在 + 属主校验（D1 §3.4 红线：不区分不存在/他属，防探测）
        if (!runtime.getBinder().isSessionOwner(sessionId, actor))
            throw new ReplAccessException(403, "nop.err.xlang.debugger.session-bound-elsewhere",
                    "session not repl-eligible: " + sessionId);
        checkSourceSize(source);
        // debugger.enabled 执行期谓词复查（D6 §一.5 运行层门控：运行期翻 false 即拒绝）
        if (!enabledPredicate.getAsBoolean())
            throw new ReplAccessException(403, "xlang debugger execution disabled");

        DebugSessionHandle handle = runtime.sessionManager().getSession(sessionId);
        if (handle == null || handle.isDestroyed())
            throw new ReplAccessException(403, "nop.err.xlang.debugger.session-bound-elsewhere",
                    "session not repl-eligible: " + sessionId);

        if (!globalPermits.tryAcquire())
            throw new ReplAccessException(429, "nop.err.xlang.debugger.rate-limited",
                    "repl global concurrency limit reached");
        if (!inFlightSessions.add(sessionId)) {
            globalPermits.release();
            throw new ReplAccessException(429, "nop.err.xlang.debugger.rate-limited",
                    "session already executing");
        }

        long timeout = timeoutMs == null ? read(XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_REPL_EXECUTE_TIMEOUT)
                : Math.min(timeoutMs, MAX_EXECUTE_TIMEOUT_MS);
        long deadline = System.currentTimeMillis() + timeout;
        ThreadLocalReplDeadlineChannel channel = ThreadLocalReplDeadlineChannel.instance();
        Map<String, Object> response;
        String auditResult;
        try {
            channel.begin(deadline);
            // 沙箱校验先于 compileFullExpr（plan 2478）；语法错误 NopException 由下方
            // catch 映射 ok:false 响应（不可逃逸到壳——壳对非 ReplAccessException 映射 500），
            // 白名单/禁用构造违规以 400 卫语句抛出
            SourceLocation replLoc = SourceLocation.fromLine("repl.xpl", 1);
            validateSandbox(source, replLoc);
            // 直接以会话融合执行器为根入口（不依赖环境注册的全局执行器——测试/嵌入
            // 场景全局链可能是裸 default，嵌套入口将绕过 deadline 检查）；自线程化保证
            // while 回边等嵌套入口 per-entry 检查。标记窗口仍保留：编译期求值路由会话
            Object value = DebugSessionScope.withSession(handle, () -> {
                ExprEvalAction action = XLang.newCompileTool()
                        .compileFullExpr(SourceLocation.fromLine("repl.xpl", 1), source);
                io.nop.core.lang.eval.EvalRuntime rt = new io.nop.core.lang.eval.EvalRuntime(
                        XLang.newEvalScope());
                return handle.getDebugExecutor().execute(action.getExpr(), rt);
            });
            response = successResponse(value);
            auditResult = "ok";
        } catch (ReplAccessException e) {
            throw e;
        } catch (ReplCancelledError e) {
            boolean isTimeout = "nop.err.xlang.debugger.repl-timeout".equals(e.getErrorCode());
            response = errorResponse(e.getErrorCode(), e.getErrorMessage(), true);
            auditResult = isTimeout ? "timeout" : "cancelled";
        } catch (NopException e) {
            response = errorResponse(e.getErrorCode(),
                    e.getDescription() != null ? e.getDescription() : e.getErrorCode(), false);
            auditResult = "error";
        } catch (Exception e) {
            LOG.error("nop.xlang.debug-web.repl-execute-fail:sessionId={}", sessionId, e);
            response = errorResponse(null, "internal error: " + e.getClass().getSimpleName(), false);
            auditResult = "error";
        } finally {
            channel.end();
            inFlightSessions.remove(sessionId);
            globalPermits.release();
        }
        response.put("durationMs", System.currentTimeMillis() - start);
        audit(actor, sessionId, "execute", source, auditResult, System.currentTimeMillis() - start);
        return response;
    }

    // ===== 内部 =====

    private Map<String, Object> successResponse(Object value) {
        Map<String, Object> response = new LinkedHashMap<>();
        String result = value instanceof String ? (String) value : JsonTool.stringify(value);
        byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_RESULT_BYTES) {
            result = new String(bytes, 0, MAX_RESULT_BYTES, StandardCharsets.UTF_8);
            response.put("truncated", true);
        }
        response.put("ok", true);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> errorResponse(String errorCode, String message, boolean cancelled) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", false);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message == null ? "" : message);
        if (errorCode != null)
            error.put("errorCode", errorCode);
        response.put("error", error);
        if (cancelled)
            response.put("cancelled", true);
        return response;
    }

    private Map<String, Object> compileErrorResult(String message, String errorCode) {
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("line", 1);
        diagnostic.put("severity", "error");
        diagnostic.put("message", message);
        diagnostic.put("errorCode", errorCode);
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        diagnostics.add(diagnostic);
        result.put("diagnostics", diagnostics);
        return result;
    }

    private void checkSourceSize(String source) {
        int max = (int) read(XLangDebuggerConfigs.CFG_XLANG_DEBUG_WEB_REPL_MAX_SOURCE_BYTES);
        if (source != null && source.getBytes(StandardCharsets.UTF_8).length > max)
            throw new ReplAccessException(413, "source exceeds " + max + " bytes");
    }

    /**
     * D6 §3.3 编译面校验（plan 2478 修订：parse-only AST 遍历 + 正则字面量 token 判别，
     * 机制与残余面见 {@link ReplSourceValidator}）。
     */
    void validateSandbox(String source, SourceLocation loc) {
        sandboxValidator.validate(source, loc, importWhitelist());
    }

    private Set<String> importWhitelist() {
        String csv = runtime.importWhitelist();
        Set<String> whitelist = Set.of();
        if (csv != null && !csv.isEmpty()) {
            whitelist = Set.of(csv.trim().split("\\s*,\\s*"));
        }
        return whitelist;
    }

    /** D6 §3.5 审计事件：八字段结构化单行；consumer 缺省 = 审计 logger。 */
    void audit(String actor, String sessionId, String action, String source, String result,
               long durationMs) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", System.currentTimeMillis());
        event.put("actor", actor);
        event.put("sessionId", sessionId);
        event.put("action", action);
        event.put("sourceDigest", sha256(source));
        event.put("sourcePreview", preview(source));
        event.put("result", result);
        event.put("durationMs", durationMs);
        Consumer<Map<String, Object>> consumer = auditConsumer;
        if (consumer != null)
            consumer.accept(event);
        else
            AUDIT_LOG.info("nop.xlang.debug.audit:{}", JsonTool.stringify(event));
    }

    static String sha256(String source) {
        if (source == null)
            return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static String preview(String source) {
        if (source == null)
            return "";
        return source.length() <= SOURCE_PREVIEW_LEN ? source : source.substring(0, SOURCE_PREVIEW_LEN);
    }

    private static long read(IConfigReference<Integer> ref) {
        Integer v = ref.get();
        return v == null ? 0L : v;
    }

    private static String readString(IConfigReference<String> ref) {
        return ref.get();
    }
}
