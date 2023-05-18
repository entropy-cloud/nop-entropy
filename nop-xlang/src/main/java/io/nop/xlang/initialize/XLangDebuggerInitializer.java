/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.initialize;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.BreakpointHitMessage;
import io.nop.api.debugger.IDebugger;
import io.nop.api.debugger.StackInfo;
import io.nop.api.debugger.ThreadInfo;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.lang.eval.DefaultExpressionExecutor;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.rpc.core.RpcConstants;
import io.nop.rpc.simple.SimpleRpcServer;
import io.nop.socket.ServerConfig;
import io.nop.xlang.debug.DebugExpressionExecutor;
import io.nop.xlang.debug.IDebugNotifier;
import io.nop.xlang.debug.SuspendedThread;
import io.nop.xlang.debug.XLangDebugger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.nop.core.CoreConstants.INITIALIZER_PRIORITY_REGISTER_XLANG;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_DEBUGGER_ENABLED;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_DEBUGGER_MAX_DATA_LEN;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_DEBUGGER_PORT;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_DEBUGGER_WAIT_CONNECTION_SECONDS;

public class XLangDebuggerInitializer implements ICoreInitializer {
    static final Logger LOG = LoggerFactory.getLogger(XLangDebuggerInitializer.class);

    private SimpleRpcServer server;
    private XLangDebugger debugger;

    @Override
    public boolean isEnabled() {
        return CFG_XLANG_DEBUGGER_ENABLED.get();
    }

    @Override
    public int order() {
        return INITIALIZER_PRIORITY_REGISTER_XLANG;
    }

    @Override
    public void initialize() {
        server = new SimpleRpcServer();
        ServerConfig config = new ServerConfig();
        config.setServerName("XLangDebuggerServer");
        config.setPort(CFG_XLANG_DEBUGGER_PORT.get());
        config.setMaxDataLen(CFG_XLANG_DEBUGGER_MAX_DATA_LEN.get());
        config.setIdleTimeout(0);
        server.setServerConfig(config);

        debugger = new XLangDebugger();
        debugger.setNotifier(new DebugNotifier());
        server.addServiceImpl(IDebugger.class, debugger);
        server.setOnChannelOpen(this::sendBreakpointNotice);

        server.start();

        int waitSeconds = CFG_XLANG_DEBUGGER_WAIT_CONNECTION_SECONDS.get();
        if (waitSeconds > 0) {
            server.waitConnected(waitSeconds * 1000);
        }
        LOG.info("nop.debugger.register-debug-executor");
        EvalExprProvider.registerGlobalExecutor(new DebugExpressionExecutor(debugger));
    }

    // 通知调试器客户端当前正在处理的断点情况
    private void sendBreakpointNotice(String addr) {
        XLangDebugger debugger = this.debugger;
        if (debugger == null || !debugger.isSuspended())
            return;

        SimpleRpcServer server = this.server;
        if (server == null)
            return;

        List<ThreadInfo> threads = debugger.getSuspendedThreads();
        for (ThreadInfo thread : threads) {
            BreakpointHitMessage message = new BreakpointHitMessage();
            StackInfo stackInfo = debugger.getStackInfo(thread.getThreadId());
            message.setStackInfo(stackInfo);
            message.setType("notice");
            ApiResponse<?> res = ApiResponse.buildSuccess(message);
            server.sendNoticeTo(addr, res);
        }
    }

    class DebugNotifier implements IDebugNotifier {
        void sendStack(SuspendedThread thread, String type) {
            BreakpointHitMessage message = new BreakpointHitMessage();
            StackInfo stackInfo = thread.getStackInfo();
            message.setStackInfo(stackInfo);
            message.setType(type);

            ApiResponse<?> res = ApiResponse.buildSuccess(message);
            server.broadcast(RpcConstants.CMD_NOTICE, (short) 0, res);
        }

        @Override
        public void notifyWarn(String message, Object... args) {

        }

        @Override
        public void notifyLog(SuspendedThread thread, String sourcePath, int line, String message) {
        }

        @Override
        public void notifySuspend(SuspendedThread thread, SourceLocation loc, IEvalScope scope) {
            sendStack(thread, "suspend");
        }

        @Override
        public void notifyStepInto(SuspendedThread thread, SourceLocation loc, IEvalScope scope) {
            sendStack(thread, "stepInto");
        }

        @Override
        public void notifyStepOver(SuspendedThread thread, SourceLocation loc, IEvalScope scope) {
            sendStack(thread, "stepOver");
        }

        @Override
        public void notifyStepOut(SuspendedThread thread, SourceLocation loc, IEvalScope scope) {
            sendStack(thread, "stepOut");
        }

        @Override
        public void notifyBreakAt(SuspendedThread thread, SourceLocation loc, IEvalScope scope) {
            sendStack(thread, "breakAt");
        }
    }

    @Override
    public void destroy() {
        EvalExprProvider.registerGlobalExecutor(DefaultExpressionExecutor.INSTANCE);
        if (server != null) {
            server.stop();
            debugger = null;
            server = null;
        }
    }
}
