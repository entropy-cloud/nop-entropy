/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;

import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.debugger.Breakpoint;
import io.nop.api.debugger.BreakpointHitMessage;
import io.nop.api.debugger.IDebuggerAsync;
import io.nop.api.debugger.LineLocation;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.event.HyperlinkListener;

import static com.intellij.xdebugger.impl.ui.DebuggerUIUtil.invokeLater;

/**
 * 在Java调试器的基础上增加XLang调试功能
 */
public class XLangDebugProcess extends JavaDebugProcess {
    static final Logger LOG = LoggerFactory.getLogger(XLangDebugProcess.class);
    private final XLangBreakpointHandler myBreakPointHandler;
    private final JavaCommandLineState state;
    private final XLangDebuggerEditorsProvider myEditorsProvider;
    boolean isDisconnected = false;

    private IDebuggerAsync debugger;

    //private final AtomicBoolean breakpointsInitiated = new AtomicBoolean();

    private final XLangDebugConnector connector;

    public XLangDebugProcess(@NotNull final XDebugSession session,
                             @NotNull final JavaCommandLineState state,
                             final DebuggerSession javaSession,
                             int debugPort
    ) {
        super(session, javaSession);
        this.state = state;

        this.myEditorsProvider = new XLangDebuggerEditorsProvider();
        this.myBreakPointHandler = new XLangBreakpointHandler(this);
        this.connector = new XLangDebugConnector(debugPort, this::debuggerNotification, this::onSocketConnected);

        javaSession.getProcess().setXDebugProcess(this);
        session.addSessionListener(new XDebugSessionListener() {
            @Override
            public void breakpointsMuted(boolean muted) {
                myBreakPointHandler.breakpointMuted(muted);
            }

            public void sessionResumed() {
                LOG.info("xlang.debugger.session-resumed");
            }

            public void sessionStopped() {
                LOG.info("xlang.debugger.session-stopped");
            }

            public void stackFrameChanged() {
                LOG.info("xlang.debugger.stack-frame-changed");
            }

            public void beforeSessionResume() {
                LOG.info("xlang.debugger.before-session-resume");
            }
        });
    }

    public IDebuggerAsync getDebugger() {
        return debugger;
    }

    @Override
    public void sessionInitialized() {
        super.sessionInitialized();

        ProgressManager.getInstance().run(new Task.Backgroundable(null, "XLang debugger", true) {
            public void run(@NotNull final ProgressIndicator indicator) {
                indicator.setText("XLang Debugger Connecting...");
                indicator.setIndeterminate(true);

                try {
                    if (!connect())
                        return;
                    startDebugSession();
                } catch (final Exception e) {
                    onConnectFail(e.getMessage());
                }
            }
        });
    }

    private boolean connect() {
        while (true) {
            try {
                debugger = connector.connect();
                System.out.println("connected");
                return true;
            } catch (final Exception e) {
                try {
                    Thread.sleep(200);
                } catch (Exception e2) {

                }
                if (getProcessHandler().isProcessTerminated())
                    return false;
            }
        }
    }

    private void onSocketConnected() {

    }

    private void onConnectFail(final String msg) {
        getProcessHandler().destroyProcess();
        invokeLater(() -> {
            String text = "XLangDebugger can't connect to DebuggerServer on port " + connector.getDebugPort();//myDebuggerProxy.getPort();
            Messages.showErrorDialog(msg != null ? text + ":\r\n" + msg : text, "XLang debugger");
        });
    }

    private void startDebugSession() {
        //initBreakpointHandlersAndSetBreakpoints();
        ReadAction.run(() -> {
            myBreakPointHandler.sendBreakpoints();
        });
    }



  /*  @NotNull
    @Override
    public ExecutionConsole createConsole() {
        /*TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(session.getProject());

        ConsoleView console = consoleBuilder.getConsole();
        console.attachToProcess(handler);
        return console;
        return super.createConsole();
      //  return state.getConsoleBuilder().getConsole();
    }
 */

    @Override
    public void resume(@Nullable XSuspendContext context) {
        if (context instanceof XLangSuspendContext) {
            if (debugger != null)
                debugger.resumeAsync();
        } else {
            super.resume(context);
        }
    }

    @Override
    public void startPausing() {
        //System.out.println("pausing ...");
        if (debugger != null) {
            debugger.suspendAsync();
        }
        super.startPausing();
    }

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
        if (context instanceof XLangSuspendContext) {
            //System.out.println("step over...");
            if (debugger != null) {
                debugger.stepOverAsync();
            }
        } else {
            super.startStepOver(context);
        }

    }

    @Override
    public void startForceStepInto(@Nullable XSuspendContext context) {
        if (context instanceof XLangSuspendContext) {
            //System.out.println("step force step into...");
            if (debugger != null) {
                debugger.stepIntoAsync();
            }
        } else {
            super.startForceStepInto(context);
        }
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
        if (context instanceof XLangSuspendContext) {
            //System.out.println("step into...");
            if (debugger != null) {
                debugger.stepIntoAsync();
            }
        } else {
            super.startStepInto(context);
        }
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
        if (context instanceof XLangSuspendContext) {
            //System.out.println("start step out...");
            if (debugger != null) {
                debugger.stepOutAsync();
            }
        } else {
            super.startStepOut(context);
        }
    }

    public void stop() {
        connector.destroy();

        super.stop();

        isDisconnected = true;
        System.out.println("end debug process");
    }

    @Override
    public XBreakpointHandler<?>[] getBreakpointHandlers() {
        XBreakpointHandler<?>[] handlers = super.getBreakpointHandlers();
        XBreakpointHandler<?>[] ret = new XBreakpointHandler<?>[handlers.length + 1];
        for (int i = 0; i < handlers.length; i++) {
            ret[i] = handlers[i];
        }
        ret[ret.length - 1] = myBreakPointHandler;
        return ret;
    }

    @Nullable
    @Override
    public XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
        return super.getSmartStepIntoHandler();
    }

    @Override
    public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
        if (context instanceof XLangSuspendContext) {
            //System.out.println("start step out...");
            if (debugger != null) {
                LineLocation line = ProjectFileHelper.toLineLocation(position);
                debugger.runToPositionAsync(Breakpoint.build(line.getSourcePath(), line.getLine()));
            }
        } else {
            super.runToPosition(position, context);
        }
    }

    @Override
    public boolean checkCanInitBreakpoints() {
        // We manually initializes the breakpoints after connecting to the debug server.
        return false;
    }

    // 接收XLang调试器发来的通知消息
    private void debuggerNotification(ApiResponse<?> response) {
        this.getDebuggerSession().getProcess().getManagerThread().schedule(PrioritizedTask.Priority.LOW, () -> {
            BreakpointHitMessage hit = BeanTool.buildBean(response.getData(), BreakpointHitMessage.class);

            XBreakpoint<XLangBreakpointProperties> breakpoint = myBreakPointHandler.findBreakPoint(
                    hit.getStackInfo().getTopElement());

            XDebugSession session = getSession();
            XSuspendContext context = session.getSuspendContext();
            if (context instanceof XLangSuspendContext) {
                ((XLangSuspendContext) context).addExecutionStack(hit.getStackInfo());
            } else {
                context = new XLangSuspendContext(this, hit.getStackInfo());
            }
            if (breakpoint == null) {
                LOG.info("debug.breakpoint_position_reached:frame={}", hit.getStackInfo().getTopElement());
                session.positionReached(context);
            } else {
                LOG.info("debug.breakpoint_reached:breakpoint={}", breakpoint);
                session.breakpointReached(breakpoint, null, context);
            }
        });
    }

    @Nullable
    @Override
    public XValueMarkerProvider<?, ?> createValueMarkerProvider() {
        return super.createValueMarkerProvider();
    }

    @Override
    public String getCurrentStateMessage() {
        return super.getCurrentStateMessage();
    }

    @Nullable
    @Override
    public HyperlinkListener getCurrentStateHyperlinkListener() {
        return super.getCurrentStateHyperlinkListener();
    }

    @NotNull
    @Override
    public XDebugTabLayouter createTabLayouter() {
        return super.createTabLayouter();
    }

    @Nullable
    @Override
    public XDebuggerEvaluator getEvaluator() {
        return super.getEvaluator();
    }

    @Override
    public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar,
                                          @NotNull DefaultActionGroup topToolbar,
                                          @NotNull DefaultActionGroup settings) {
        super.registerAdditionalActions(leftToolbar, topToolbar, settings);
        //topToolbar.remove(ActionManager.getInstance().getAction(XDebuggerActions.RUN_TO_CURSOR));
    }

    @NotNull
    @Override
    public XDebuggerEditorsProvider getEditorsProvider() {
        return myEditorsProvider;
    }

    @Override
    public boolean checkCanPerformCommands() {
        return super.checkCanPerformCommands();
    }

    @Override
    public void logStack(@NotNull XSuspendContext suspendContext, @NotNull XDebugSession session) {
        super.logStack(suspendContext, session);
    }
}
