/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.i18n;

public interface II18nMessageManager {

    boolean isLocaleEnabled(String locale);

    /**
     * 根据指定的key替换为多语言字符串。允许指定多个key, 依次尝试，如果仍未找到，则返回缺省值。 如果以?结尾，则缺省值为null, 否则缺省值取为字符串本身 例如 @i18n:a.b.c,e.f.g|缺省值
     */
    String resolveI18nVar(String locale, String message);

    /**
     * 根据key获取多语言字符串， 如果该语言中没有定义对应字符串，则返回defaultValue
     *
     * @param locale 如果指定的locale没有对应的配置，则使用缺省的locale
     */
    String getMessage(String locale, String key, String defaultValue);
}
