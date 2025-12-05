/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tool.log;

import io.nop.commons.util.DateHelper;

public class LogAnalyzeHelper {
    public static boolean isLogMessageStart(String line) {
        if (line.length() < 20)
            return false;

        char c = line.charAt(10);
        if (c != 'T')
            return false;

        try {
            DateHelper.parseDate(line.substring(0, 10));
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
