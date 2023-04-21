/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.debug;

import io.nop.api.core.json.JSON;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.debugger.Breakpoint;
import io.nop.api.debugger.IBreakpointManager;
import io.nop.api.debugger.IDebugger;
import io.nop.api.debugger.IDebuggerAsync;
import io.nop.api.debugger.StackInfo;
import io.nop.api.debugger.ThreadInfo;
import io.nop.commons.util.DestroyHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.rpc.simple.SimpleRpcClientFactory;
import io.nop.rpc.simple.SimpleRpcServer;
import io.nop.socket.ClientConfig;
import io.nop.socket.ServerConfig;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.BlockingQueue;

import static io.nop.api.core.util.SourceLocation.fromLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRemoteDebugger {
    IDebuggerAsync debugger;
    SimpleRpcClientFactory<IDebuggerAsync> clientFactory;
    SimpleRpcServer server;

    XLangDebugger localDebugger;
    CollectNotifier notifier;

    @BeforeEach
    public void setUp() {
        JSON.registerProvider(JsonTool.instance());
        server = new SimpleRpcServer();
        server.setServerConfig(new ServerConfig());
        server.getServerConfig().setPort(12345);
        server.getServerConfig().setIdleTimeout(0);

        localDebugger = new XLangDebugger();
        localDebugger.setUseExternalPath(false);
        notifier = new CollectNotifier();
        localDebugger.setNotifier(notifier);
        server.addServiceImpl(IDebugger.class, localDebugger);

        server.start();

        clientFactory = new SimpleRpcClientFactory<>();
        clientFactory.setClientConfig(new ClientConfig());
        clientFactory.getClientConfig().setPort(12345);
        clientFactory.getClientConfig().setReadTimeout(0);
        clientFactory.getClientConfig().setResponseTimeout(10000);
        clientFactory.setRpcInterface(IDebuggerAsync.class);
        clientFactory.setServiceName(IDebugger.class.getName());
        debugger = clientFactory.newInstance();
    }

    @Test
    public void testDebug() throws Exception {
        IBreakpointManager bpm = debugger;
        for (int i = 0; i < 10; i++) {
            // 每10行增加一个断点，例如 1, 11, 21
            if (i == 0)
                continue;
            bpm.addBreakpoint(Breakpoint.build("a", i * 10 + 1));
            bpm.addBreakpoint(Breakpoint.build("b", i * 10 + 1));
        }

        debugger.runToPosition(Breakpoint.build("a", 5));

        new Thread(() -> {
            IEvalScope scope = XLang.newEvalScope();
            scope.pushFrame(new EvalFrame(null, new String[]{"x", "y"}, new Object[]{"x1", "y1"}));

            for (int i = 0; i < 100; i++) {
                localDebugger.checkBreakpoint(fromLine("a", i + 1), scope);

                if (i == 10) {
                    // 模拟函数调用
                    scope.pushFrame(new EvalFrame(scope.getCurrentFrame(), new String[]{"u", "v"},
                            new Object[]{"u1", "v1"}));

                    for (int j = 0; j < 10; j++) {
                        localDebugger.checkBreakpoint(fromLine("b", j + 1), scope);
                    }
                    scope.popFrame();
                }
            }
            notifier.getMessages().add("end");
        }).start();

        BlockingQueue<String> messages = notifier.getMessages();
        assertEquals("break:[5:0:0:0]a", messages.take());
        assertBreakAt("a(5)", debugger);
        FutureHelper.syncGet(debugger.resumeAsync());

        assertEquals("break:[11:0:0:0]a", messages.take());
        assertBreakAt("a(11)", debugger);

        debugger.stepInto();
        assertEquals("stepInto:[1:0:0:0]b", messages.take());
        assertBreakAt("b(1)", debugger);
    }

    void assertBreakAt(String path, IDebugger debugger) {
        if(!debugger.isSuspended()){
            try{
                Thread.sleep(100);
            }catch (Exception e){}
        }
        assertTrue(debugger.isSuspended());
        assertEquals(path, getLastBreakLocation(debugger));
    }

    StackInfo getStackTrace(IDebugger debugger) {
        List<ThreadInfo> threads = debugger.getSuspendedThreads();
        return debugger.getStackInfo(threads.get(0).getThreadId());
    }

    String getLastBreakLocation(IDebugger debugger) {
        return StringHelper.toString(getStackTrace(debugger).getTopLocation(), null);
    }

    @AfterEach
    public void tearDown() {
        clientFactory.destroy();
        server.stop();
        DestroyHelper.safeDestroy(debugger);
        server.stop();
    }

    @Test
    public void testBinary() {
        String s = "a";
        s = s + 1 + 2 + (3 > 0 ? 'a' : 'b');
    }
}
