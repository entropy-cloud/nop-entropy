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
 * 从System.getenv()装载的配置。环境变量名会按照一定的规则映射到配置变量名， 参见{@link StringHelper#envToConfigVar(String)}
 */
public class EnvConfigSourceLoader implements IConfigSourceLoader {
    private static final SourceLocation S_LOC = SourceLocation.fromClass(EnvConfigSourceLoader.class);

    @Override
    public IConfigSource loadConfigSource(IConfigSource config) {
        Map<String, ValueWithLocation> ret = new HashMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String name = StringHelper.envToConfigVar(entry.getKey());
            if (!StringHelper.isValidConfigVar(name))
                continue;

            String value = entry.getValue();
            ret.put(name, ValueWithLocation.of(S_LOC, value));
        }
        return new StaticConfigSource("env", ret);
    }
}
