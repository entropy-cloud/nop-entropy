/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.xlang.debugger.XLangDebugger;

/**
 * 会话生命周期回调（D3 §3.5 notifier 接线/拆线的挂点）：网关（item 12）注册 listener，
 * 在回调中做专属 {@link IDebugNotifier} 的接线与拆线。
 *
 * <p>契约：回调在 manager 监视器锁外触发；回调抛出的异常被 manager 隔离（catch+log），
 * 不中断创建/销毁链。
 */
public interface SessionListener {
    void onCreated(DebugSessionHandle handle, XLangDebugger debugger);

    void onDestroyed(DebugSessionHandle handle, XLangDebugger debugger);
}
