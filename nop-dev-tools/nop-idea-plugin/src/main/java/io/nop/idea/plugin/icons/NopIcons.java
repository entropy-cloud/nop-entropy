/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.icons;

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
    /** 文件类型图标: 16x16 */
    public static final Icon XLangFileType = getIcon("/icons/xlang.svg", NopIcons.class);

    /** 底部工具栏的图标: 13x13 */
    public static final Icon Tool_XLangDebug = getIcon("/icons/xlangDebug-small.svg", NopIcons.class);
    /** 顶部调试启动按钮图标: 16x16 */
    public static final Icon Action_XLangDebug = getIcon("/icons/xlangDebug.svg", NopIcons.class);

    private NopIcons() {
    }
}
