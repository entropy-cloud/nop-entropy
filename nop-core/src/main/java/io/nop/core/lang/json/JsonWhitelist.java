/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json;

import io.nop.api.core.annotations.core.GlobalInstance;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 允许明确注册的bean可以被序列化。缺省情况下只有标记了{@link io.nop.api.core.annotations.data.DataBean} 注解的对象可以被json序列化
 */
@GlobalInstance
public class JsonWhitelist {
    public static final Set<String> DEFAULTS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void addDefault(String className) {
        DEFAULTS.add(className);
    }
}
