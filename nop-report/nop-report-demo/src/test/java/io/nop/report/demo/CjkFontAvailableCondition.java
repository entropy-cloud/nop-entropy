/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.demo;

import java.io.File;

/**
 * Test gating helper for CJK-font dependent PDF export audits.
 *
 * <p>When no CJK font can be located on the system, the audit tests are
 * skipped because they assume a system font fallback chain (WQY on Linux,
 * Arial Unicode on macOS, simsun.ttc on Windows). The CI runner image
 * installs fonts-wqy-microhei; bare developer machines frequently do not.
 *
 * <p>Used together with {@code @EnabledIf} on CJK-font dependent audit tests.
 */
final class CjkFontAvailableCondition {
    private CjkFontAvailableCondition() {
    }

    private static final String[] CJK_FONT_CANDIDATES = {
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf", // macOS
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",       // Linux
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",      // Linux fallback
            "C:/Windows/Fonts/simsun.ttc",                          // Windows
            "C:/Windows/Fonts/msyh.ttc",                            // Windows 微软雅黑
            "C:/Windows/Fonts/arial.ttf",                           // Windows Arial
    };

    static boolean isCjkFontAvailable() {
        for (String path : CJK_FONT_CANDIDATES) {
            if (new File(path).exists()) {
                return true;
            }
        }
        return false;
    }
}