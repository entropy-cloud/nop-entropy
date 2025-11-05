/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 从System.getProperties()集合装载的配置
 */
public class SysPropertyConfigSourceLoader implements IConfigSourceLoader {
    @Override
    public IConfigSource loadConfigSource(IConfigSource currentConfig) {
        SourceLocation loc = SourceLocation.fromClass(SysPropertyConfigSourceLoader.class);
        Map<String, ValueWithLocation> ret = new HashMap<>();
        for (Object key : System.getProperties().keySet()) {
            String name = StringHelper.normalizeConfigVar((String) key);

            if (!StringHelper.isValidConfigVar(name))
                continue;

            String value = System.getProperty(name);

            ret.put(name, ValueWithLocation.of(loc, value));
        }
        return new StaticConfigSource("properties", ret);
    }
}
