/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XNavigatable;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.impl.ui.tree.nodes.EvaluatingExpressionRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import io.nop.api.debugger.DebugValueKey;
import io.nop.api.debugger.DebugVariable;
import io.nop.api.debugger.LineLocation;
import io.nop.commons.type.StdDataType;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class XLangValue extends XValue {

    private final XLangStackFrame frame;
    private final DebugVariable var;

    public XLangValue(XLangStackFrame frame, DebugVariable var) {
        this.var = var;
        this.frame = frame;
    }

    public DebugVariable getVar() {
        return var;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        TreeNode parent = (XValueNodeImpl) node;
        List<DebugVariable> list = new ArrayList<>();
        while (parent != null && !(parent instanceof WatchesRootNode) && !(parent instanceof EvaluatingExpressionRootNode)) {
            XLangValue var = (XLangValue) ((XValueNodeImpl) parent).getValueContainer();
            list.add(var.getVar());
            parent = parent.getParent();
        }

        Collections.reverse(list);

        // 第一个变量对应于表达式本身。例如WatchExpression所对应的表达式
        String expr = list.get(0).getName();
        List<DebugValueKey> keys = list.subList(1, list.size())
                .stream().map(v -> v.getValueKey()).collect(Collectors.toList());

        frame.getDebugProcess().getDebugger()
                .expandExprValueAsync(frame.getThreadId(), frame.getFrameIndex(), expr, keys)
                .whenComplete((expandedVars, err) -> {
                    if (err != null) {
                        node.setErrorMessage(err.getMessage());
                    } else {
                        XValueChildrenList children = new XValueChildrenList();
                        if (expandedVars != null) {
                            for (DebugVariable var : expandedVars) {
                                children.add(var.getName(), new XLangValue(frame, var));
                            }
                        }
                        node.addChildren(children, true);
                    }
                });
    }

    public boolean isPlainObjectType() {
        String className = var.getType();
        if (className == null ||
                StdDataType.isSimpleType(className)
        ) {
            return true;
        }
        return false;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace xValuePlace) {
        String typeStr = var.getType();
        if (typeStr == null)
            typeStr = "";
        if (var.getHash() != 0) {
            typeStr += "@" + Integer.toHexString(var.getHash());
        }
        node.setPresentation(getValueIcon(), typeStr,
                var.getValue() == null ? "null" : var.getValue(), !isPlainObjectType());
    }

    public Icon getValueIcon() {
        Icon nodeIcon = null;

        if ("scope".equals(var.getKind())) {
            return AllIcons.Nodes.Static;
        }

        if (var.getScope() != null) {
            if ("public".equals(var.getScope()))
                return PlatformIcons.PUBLIC_ICON;
            if ("protected".equals(var.getScope()))
                return PlatformIcons.PROJECT_ICON;
            if ("private".equals(var.getScope())) {
                return PlatformIcons.PRIVATE_ICON;
            }
            nodeIcon = PlatformIcons.PUBLIC_ICON;
        } else if (var.getType() != null) {
            if (var.getType().contains("[") && var.getType().contains("]")) {
                nodeIcon = AllIcons.Debugger.Db_array;
            } else if ("error".equals(var.getKind())) {
                nodeIcon = AllIcons.Nodes.ExceptionClass;
            }
        } else if (
                isPlainObjectType()) {
            nodeIcon = AllIcons.Debugger.Db_primitive;
        } else {
            nodeIcon = AllIcons.Debugger.Value;
        }

        return nodeIcon;
    }

    public void computeSourcePosition(@NotNull XNavigatable navigatable) {
        LineLocation assignLoc = var.getAssignLoc();
        XSourcePosition pos = ProjectFileHelper.buildPos(assignLoc);
        navigatable.setSourcePosition(pos);
    }

}
