/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xui.initialize;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.domain.StringStdDomainHandler;

/**
 * 图标名标准域。用于 NopAuthResource.icon 等字段，通过 {@code stdDomain="icon"} 驱动
 * xui 控件选择（{@code edit-icon} / {@code view-icon}），取值是前端图标库中的图标名
 * （如 {@code settings}、{@code flask-conical}），按普通字符串处理。
 */
public class IconStdDomainHandler extends StringStdDomainHandler {
    public static final String STD_DOMAIN_ICON = "icon";

    public static final IconStdDomainHandler INSTANCE = new IconStdDomainHandler();

    @Override
    public String getName() {
        return STD_DOMAIN_ICON;
    }

    @Override
    public Object parseProp(String options, SourceLocation loc, String propName, Object value, XLangCompileTool cp) {
        return value == null ? null : value.toString();
    }
}
