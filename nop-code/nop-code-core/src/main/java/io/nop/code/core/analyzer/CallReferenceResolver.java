package io.nop.code.core.analyzer;

import java.util.function.Function;

import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.semantic.EdgeConfidence;

/**
 * 调用引用解析的共享语义：calleeQualifiedName → 符号 id，精确匹配优先，失败后剥离参数部分
 * （"pkg.Type.method(User)" → "pkg.Type.method"）再试。解析成功置 EXTRACTED，失败置 INFERRED。
 * 全量流（{@link ProjectAnalyzer#resolveCalls}）与增量/单文件索引路径共用本实现，避免双份语义漂移。
 */
public final class CallReferenceResolver {

    private CallReferenceResolver() {
    }

    /**
     * 就地解析一条调用。仅处理 calleeQualifiedName 非空的调用；返回是否解析成功。
     * lookup 收到精确 qn 或去参 qn，返回目标符号或 null。
     */
    public static boolean resolveCall(CodeMethodCall call, Function<String, CodeSymbol> lookup) {
        String calleeQn = call.getCalleeQualifiedName();
        if (calleeQn == null || calleeQn.isEmpty()) {
            return false;
        }
        CodeSymbol callee = lookup.apply(calleeQn);
        if (callee == null) {
            String withoutParams = withoutParams(calleeQn);
            if (withoutParams != null) {
                callee = lookup.apply(withoutParams);
            }
        }
        if (callee != null) {
            call.setCalleeId(callee.getId());
            call.setConfidence(EdgeConfidence.EXTRACTED);
            return true;
        }
        call.setConfidence(EdgeConfidence.INFERRED);
        return false;
    }

    /**
     * 剥离方法签名中的参数部分；无参数括号时返回 null（与旧 fuzzyMatchSymbol 行为一致）。
     */
    public static String withoutParams(String calleeQualifiedName) {
        int parenIndex = calleeQualifiedName.indexOf('(');
        if (parenIndex > 0) {
            return calleeQualifiedName.substring(0, parenIndex);
        }
        return null;
    }
}
