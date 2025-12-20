/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.utils;

import io.nop.api.core.config.AppConfig;
import io.nop.core.exceptions.ErrorMessageManager;

/**
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-12-19
 */
public class ExceptionHelper {

    /** 获取真正的异常信息 */
    public static String getExceptionMessage(Throwable e) {
        String msg = ErrorMessageManager.instance()
                                        .buildErrorMessage(AppConfig.defaultLocale(), e, false, false, false)
                                        .getDescription();
        if (msg == null) {
            msg = e.getMessage();
        }
        return msg;
    }
}
