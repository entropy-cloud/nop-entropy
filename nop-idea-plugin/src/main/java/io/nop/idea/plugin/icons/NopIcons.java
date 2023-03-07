/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.icons;

import com.intellij.icons.AllIcons;

import javax.swing.*;

import static com.intellij.openapi.util.IconLoader.getIcon;

// copy from reasonml-idea-plugin

/*
 * https://www.jetbrains.org/intellij/sdk/docs/reference_guide/work_with_icons_and_images.html
 * https://jetbrains.design/intellij/principles/icons/
 * https://jetbrains.design/intellij/resources/icons_list/
 *
 * Node, action, filetype : 16x16
 * Tool windowing            : 13x13
 * Editor gutter          : 12x12
 * Font                   : Gotham
 */
public class NopIcons {

    public static final Icon MODULE_TYPE = getIcon("/icons/type.svg", NopIcons.class);

    public static final Icon FUNCTOR = AllIcons.Nodes.Artifact;
    public static final Icon LET = AllIcons.Nodes.Variable;
    public static final Icon VAL = AllIcons.Nodes.Variable;
    public static final Icon ATTRIBUTE = AllIcons.Nodes.Property;
    public static final Icon FUNCTION = AllIcons.Nodes.Function;
    public static final Icon METHOD = AllIcons.Nodes.Method;
    public static final Icon CLASS = AllIcons.Nodes.Class;
    public static final Icon EXCEPTION = AllIcons.Nodes.ExceptionClass;
    public static final Icon EXTERNAL = AllIcons.Nodes.Enum;
    public static final Icon OBJECT = AllIcons.Json.Object;

    public static final Icon VIRTUAL_NAMESPACE = AllIcons.Actions.GroupByPackage;
    public static final Icon OPEN = AllIcons.Actions.GroupByModule;
    public static final Icon INCLUDE = AllIcons.Actions.GroupByModule;

    public static final Icon OVERLAY_MANDATORY = AllIcons.Ide.ErrorPoint;
    public static final Icon OVERLAY_EXECUTE = AllIcons.Nodes.RunnableMark;

    public static final Icon RESET = getIcon("/icons/reset.svg", NopIcons.class);

    // REPL

    public static final Icon REPL = AllIcons.Nodes.Console;

    private NopIcons() {
    }
}
