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
     * 使用默认浏览器打开链接
     */
    public static void openBrowser(String url) {
        Desktop desktop = Desktop.getDesktop();
        if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                URI uri = new URI(url);
                desktop.browse(uri);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }
}