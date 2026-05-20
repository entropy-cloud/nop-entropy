/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
    DigestedText RATE_LIMIT = loadScript("classpath:/nop/redis/rate_limit.lua");
    DigestedText GET_AND_EXPIRE = loadScript("classpath:/nop/redis/get_and_expire.lua");
    DigestedText GET_AND_SET = loadScript("classpath:/nop/redis/get_and_set.lua");
    DigestedText PUT_IF_ABSENT_OR_MATCH = loadScript("classpath:/nop/redis/put_if_absent_or_match.lua");
}
