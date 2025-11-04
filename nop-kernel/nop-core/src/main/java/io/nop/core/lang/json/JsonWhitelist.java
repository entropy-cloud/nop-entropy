/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 允许明确注册的bean可以被序列化。缺省情况下只有标记了{@link io.nop.api.core.annotations.data.DataBean} 注解的对象可以被json序列化
 */
@GlobalInstance
public class JsonWhitelist {
    private static final Set<String> whitelistClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> whitelistPrefixes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void addResource(IResource resource) {
        String text = ResourceHelper.readText(resource);
        String[] lines = StringHelper.splitToLines(text);
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty())
                continue;
            add(line);
        }
    }

    /**
     * 可以是a.b.c全类名，或者a.b.*这种包前缀匹配表达式
     *
     * @param className 类名或者包前缀匹配
     */
    public static void add(String className) {
        if (className.endsWith(".*")) {
            whitelistPrefixes.add(className.substring(className.length() - 1));
        } else {
            whitelistClasses.add(className);
        }
    }

    public static boolean contains(String className) {
        if (whitelistClasses.contains(className))
            return true;
        for (String packagePrefix : whitelistPrefixes) {
            if (className.startsWith(packagePrefix))
                return true;
        }
        return false;
    }
}
