/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.xlang.debugger.XLangDebugger;
import io.nop.xlang.debugger.session.DebugSessionHandle;
import io.nop.xlang.debugger.session.SessionListener;

/**
 * 网关侧 SessionListener（plan 2471）：把 manager 的会话创建/销毁回调委派给
 * {@link DebugSessionBinder}（onCreated 接线出站桥；onDestroyed 推 evt.sessionClosed
 * 并清理绑定状态）。manager 保证回调锁外触发且异常隔离。
 */
public class XLangDebuggerSessionListener implements SessionListener {
    private final DebugSessionBinder binder;

    public XLangDebuggerSessionListener(DebugSessionBinder binder) {
        this.binder = binder;
    }

    @Override
    public void onCreated(DebugSessionHandle handle, XLangDebugger debugger) {
        binder.onSessionCreated(handle, debugger);
    }

    @Override
    public void onDestroyed(DebugSessionHandle handle, XLangDebugger debugger) {
        binder.onSessionDestroyed(handle, debugger);
    }
}
