/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.debug;

import io.nop.api.debugger.Breakpoint;
import io.nop.api.debugger.DebugValueKey;
import io.nop.api.debugger.DebugVariable;
import io.nop.api.debugger.IBreakpointManager;
import io.nop.api.debugger.StackInfo;
import io.nop.api.debugger.ThreadInfo;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static io.nop.api.core.util.SourceLocation.fromLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXLangDebugger {

    @Test
    public void testDebug() throws Exception {
        XLangDebugger debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        CollectNotifier notifier = new CollectNotifier();
        debugger.setNotifier(notifier);
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
            Map<String, Object> z = new HashMap<>();
            z.put("a", "a1");
            scope.setLocalValue(null, "z", z);
            scope.pushFrame(new EvalFrame(null, new String[]{"x", "y"}, new Object[]{"x1", "y1"}));

            for (int i = 0; i < 100; i++) {
                debugger.checkBreakpoint(fromLine("a", i + 1), scope);

                if (i == 10) {
                    // 模拟函数调用
                    scope.pushFrame(new EvalFrame(scope.getCurrentFrame(), new String[]{"u", "v"},
                            new Object[]{"u1", "v1"}));

                    for (int j = 0; j < 10; j++) {
                        debugger.checkBreakpoint(fromLine("b", j + 1), scope);
                    }
                    scope.popFrame();
                }
            }
            notifier.getMessages().add("end");
        }).start();

        BlockingQueue<String> messages = notifier.getMessages();
        assertEquals("break:[5:0:0:0]a", messages.take());
        assertBreakAt("a(5)", debugger);
        debugger.resume();

        assertEquals("break:[11:0:0:0]a", messages.take());
        assertBreakAt("a(11)", debugger);

        debugger.stepInto();
        assertEquals("stepInto:[1:0:0:0]b", messages.take());
        assertBreakAt("b(1)", debugger);

        long threadId = debugger.getSuspendedThreads().get(0).getThreadId();
        assertEquals("u1a", debugger.getExprValue(threadId, 0, "u+'a'").getValue());
        assertEquals("y1b", debugger.getExprValue(threadId, 1, "y+'b'").getValue());

        debugger.stepOver();
        assertEquals("stepOver:[2:0:0:0]b", messages.take());
        assertBreakAt("b(2)", debugger);

        debugger.stepInto();
        assertEquals("stepInto:[3:0:0:0]b", messages.take());
        assertBreakAt("b(3)", debugger);

        assertEquals("a1", debugger.getExprValue(threadId, 0, "z.a").getValue());
        assertEquals("[a]", debugger.getExprValue(threadId, 0, "z.keySet()").getValue());
        List<DebugVariable> vars = debugger.expandExprValue(threadId, 0, "z.keySet()", null);
        assertEquals(1, vars.size());
        List<DebugValueKey> keys = new ArrayList<>();
        keys.add(vars.get(0).getValueKey());
        assertEquals(0, debugger.expandExprValue(threadId, 0, "z.keySet()", keys).size());

        debugger.stepOut();
        assertEquals("stepOut:[12:0:0:0]a", messages.take());
        assertBreakAt("a(12)", debugger);

        debugger.resume();
        assertEquals("break:[21:0:0:0]a", messages.take());
        assertBreakAt("a(21)", debugger);

        debugger.muteBreakpoints(true);
        debugger.resume();
        assertEquals("end", messages.take());
    }

    void assertBreakAt(String path, IXLangDebugger debugger) {
        assertTrue(debugger.isSuspended());
        assertEquals(path, getLastBreakLocation(debugger));
    }

    StackInfo getStackTrace(IXLangDebugger debugger) {
        List<ThreadInfo> threads = debugger.getSuspendedThreads();
        return debugger.getStackInfo(threads.get(0).getThreadId());
    }

    String getLastBreakLocation(IXLangDebugger debugger) {
        return StringHelper.toString(getStackTrace(debugger).getTopLocation(), null);
    }
}
