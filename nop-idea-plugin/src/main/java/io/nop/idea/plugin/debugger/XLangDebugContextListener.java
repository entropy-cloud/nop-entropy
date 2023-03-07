/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.debugger;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 监听java调试器消息。XLang调试器消息通过XLangDebugConnector提供，不经过此接口
 */
public class XLangDebugContextListener implements DebuggerContextListener {
    static final Logger LOG = LoggerFactory.getLogger(XLangDebugContextListener.class);
    private final XDebugSession session;
    private final DebuggerSession myJavaSession;

    public XLangDebugContextListener(XDebugSession session, DebuggerSession javaSession) {
        this.myJavaSession = javaSession;
        this.session = session;
    }


    @Override
    public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
        LOG.debug("nop.context-event:{}", event);
        if (event == DebuggerSession.Event.PAUSE
                || event == DebuggerSession.Event.CONTEXT
                || event == DebuggerSession.Event.REFRESH
                || event == DebuggerSession.Event.REFRESH_WITH_STACK
                && myJavaSession.isPaused()) {
            final SuspendContextImpl newSuspendContext = newContext.getSuspendContext();
            if (newSuspendContext != null) {
                XSuspendContext suspendContext = session.getSuspendContext();
                // JavaDebugProcess假设了SuspendContext为SuspendContextImpl类型，
                // 因此在调试xlang断点的过程中如果触发java断点, 将会出现类型转换异常。
                // 这里通过取消当前的xlang断点来临时处理一下
                if (suspendContext instanceof XLangSuspendContext) {
                    try {
                        session.resume();
                    } catch (Exception e) {
                    }
                }
            }
        }
    }
}
