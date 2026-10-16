/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.util.SourceLocation;
import java.util.List;
import io.nop.api.debugger.IDebugger;
import io.nop.core.lang.eval.EvalRuntime;

public interface IXLangDebugger extends IDebugger {

    /**
     * 租约续期（D2 §3.7）：推进会话租约截止时间（now + ttl）。仅推进，不改变挂起态。
     */
    void renewLease(long ttlMillis);

    /**
     * 当前租约截止时间（epoch ms）；0 = 租约禁用（默认会话/IDEA 场景）。
     */
    long getLeaseDeadlineMillis();

    /**
     * 当前挂起线程 id 列表（LeaseState.suspendedThreadIds 载体）。
     */
    List<Long> getSuspendedThreadIds();

    /**
     * releaseAll（D2 §3.3 宽限路径原语）：全部挂起线程局部放行（resume 语义），不置
     * closed 不抛错；聚合发 notifyLeaseExpired。
     */
    void releaseAll();

    /**
     * 定向唤醒原语（D3 §3.6.3）：仅置目标线程局部放行标志；no-op 幂等；不发事件。
     */
    void releaseThread(long threadId);

    /**
     * 调试器是否已关闭（close 语义后为 true）。
     */
    boolean isClosed();
    void checkBreakpoint(@Name("loc") SourceLocation loc, EvalRuntime rt);
}
