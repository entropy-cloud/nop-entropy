/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.nosql.core.script;

import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.DigestedText;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;

public interface RedisScripts {
    static DigestedText loadScript(String path) {
        IResource resource = new ClassPathResource(path);
        return new DigestedText(ResourceHelper.readText(resource, StringHelper.ENCODING_UTF8));
    }

    DigestedText REMOVE_IF_MATCH = loadScript("classpath:/nop/redis/remove_if_match.lua");
}
