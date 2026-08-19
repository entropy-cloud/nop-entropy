package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.exception.AbstractTruffleException;

/**
 * XLang 语言异常的 Truffle 包装：根节点捕获语言异常（{@code NopException} 族）后包装抛出，
 * 原始异常保持为 cause（经求值 handoff 记录原样回传宿主侧，三层断言语料不丢失）。
 *
 * <p>包装动机：非 Truffle 异常穿越引擎边界按 internal error 处理可能使 Context 失效；
 * AbstractTruffleException 是引擎认可的语言异常载体，且可携带 SourceSection。
 * 控制流异常族（ExitMode → ControlFlowException）归 I7，届时在根节点区分放行。
 */
final class XLangTruffleException extends AbstractTruffleException {

    private static final long serialVersionUID = 1L;

    XLangTruffleException(Throwable cause, com.oracle.truffle.api.nodes.Node location) {
        super(cause.getMessage(), cause, UNLIMITED_STACK_TRACE, location);
    }
}
