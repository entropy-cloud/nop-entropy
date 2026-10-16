/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

/**
 * REPL 取消/超时异常（D6 §3.2 裁定）：Error 系——绕过脚本的 catch(Exception)
 * （TryExecutable 无差别 catch(Exception)，Exception 系取消必被吞 + 吞后 livelock）；
 * 同步解释器主路径已核实无 catch(Throwable)。errorCode 承载 REPL_CANCELLED /
 * REPL_TIMEOUT 二值语义（nop.err.xlang.debugger.repl-cancelled / repl-timeout）。
 */
public class ReplCancelledError extends Error {
    private final String errorCode;

    public ReplCancelledError(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
