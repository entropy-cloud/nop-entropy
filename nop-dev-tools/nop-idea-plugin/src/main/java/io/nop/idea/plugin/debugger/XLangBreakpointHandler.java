/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;


import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import io.nop.api.debugger.Breakpoint;
import io.nop.api.debugger.IDebuggerAsync;
import io.nop.api.debugger.StackTraceElement;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 连接上XLang调试器之后，将IDE中的断点信息传送到远端调试服务器
 */
public class XLangBreakpointHandler extends XBreakpointHandler<XLineBreakpoint<XLangBreakpointProperties>> {

    static final Logger LOG = LoggerFactory.getLogger(XLangBreakpointHandler.class);

    private final Map<String, XLineBreakpoint<XLangBreakpointProperties>> breakpoints = new HashMap<>();
    private final XLangDebugProcess debugProcess;
    private boolean muted;

    private final Map<String, Breakpoint> bpMap = new HashMap<>();

    public XLangBreakpointHandler(final XLangDebugProcess debugProcess) {
        super(XLangBreakpointType.class);
        this.debugProcess = debugProcess;
    }

    public void breakpointsMuted(boolean muted) {
        this.muted = muted;
        IDebuggerAsync debugger = debugProcess.getDebugger();
        if (debugger != null) {
            debugger.muteBreakpointsAsync(muted);
        }
    }

    @Override
    public void registerBreakpoint(@NotNull final XLineBreakpoint<XLangBreakpointProperties> breakpoint) {
        Breakpoint bp = makeBreakpoint(breakpoint);
        if (bp == null)
            return;

        String key = getKey(bp);
        breakpoints.put(key, breakpoint);
        bpMap.put(key, bp);

        debugProcess.getSession().updateBreakpointPresentation(breakpoint, AllIcons.Debugger.Db_verified_breakpoint, null);

        sendBreakpoints();
    }

    @Override
    public void unregisterBreakpoint(@NotNull final XLineBreakpoint<XLangBreakpointProperties> breakpoint, final boolean temporary) {
        Breakpoint bp = makeBreakpoint(breakpoint);
        if (bp == null)
            return;

        String key = getKey(bp);
        breakpoints.remove(key);
        bpMap.remove(key);

        sendBreakpoints();
    }

    private String getKey(Breakpoint bp) {
        return bp.getSourcePath() + ':' + bp.getLine();
    }

    private Breakpoint makeBreakpoint(XLineBreakpoint<?> breakpoint) {
        final XSourcePosition sourcePosition = breakpoint.getSourcePosition();
        if (sourcePosition == null || !sourcePosition.getFile().exists() || !sourcePosition.getFile().isValid()) {
            // ???
            return null;
        }

        final VirtualFile file = sourcePosition.getFile();
        final Project project = debugProcess.getSession().getProject();
        final String fileURL = ProjectFileHelper.getFileUrl(file);
        final int lineNumber = ProjectFileHelper.getActualLineNumber(project, breakpoint.getSourcePosition());
        if (lineNumber == -1) {
            debugProcess.getSession().setBreakpointInvalid(breakpoint, "Unsupported breakpoint position");
            return null;
        }

        String condition = null;
        if (breakpoint.getConditionExpression() != null) {
            condition = breakpoint.getConditionExpression().getExpression();
        }

        String logExpr = null;
        if (breakpoint.getLogExpressionObject() != null) {
            logExpr = breakpoint.getLogExpressionObject().getExpression();
        }
        return new Breakpoint(fileURL, lineNumber, condition, logExpr);
    }

    public void sendBreakpoints() {
        IDebuggerAsync debugger = debugProcess.getDebugger();
        if (debugger != null) {
            List<Breakpoint> bps = new ArrayList<>(bpMap.values());
            debugger.updateBreakpointsAsync(bps, muted)
                    .exceptionally(e -> {
                        debugProcess.getSession().reportMessage("Update breakpoints fail", MessageType.ERROR);
                        return null;
                    });
        }
    }

    public XBreakpoint<XLangBreakpointProperties> findBreakPoint(@NotNull StackTraceElement elm) {
        String path = elm.getSourcePath();
        int lineNumber = elm.getLine();
        return breakpoints.get(path + ':' + lineNumber);
    }

}
