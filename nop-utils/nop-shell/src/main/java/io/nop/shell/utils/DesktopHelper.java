/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.shell.utils;

import io.nop.api.core.exceptions.NopException;

import java.awt.*;
import java.net.URI;

public class DesktopHelper {
    /**
     * 使用默认浏览器打开链接。无桌面环境（headless 服务器）时静默跳过。
     */
    public static void openBrowser(String url) {
        // 必须先检查 isDesktopSupported：headless 环境下 getDesktop() 会直接抛 HeadlessException，
        // 后置的 isSupported 检查将永远无法起到保护作用
        if (!Desktop.isDesktopSupported())
            return;
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                URI uri = new URI(url);
                desktop.browse(uri);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }
}