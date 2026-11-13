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
    DigestedText PUT_IF_ABSENT_OR_MATCH = loadScript("classpath:/nop/redis/put_if_absent_or_match.lua");
    DigestedText HASH_REMOVE_IF_MATCH = loadScript("classpath:/nop/redis/hash_remove_if_match.lua");
    DigestedText HASH_PUT_IF_ABSENT_OR_MATCH = loadScript("classpath:/nop/redis/hash_put_if_absent_or_match.lua");
    DigestedText SESSION_SET_FIELD = loadScript("classpath:/nop/redis/session_set_field.lua");
    DigestedText SESSION_SET = loadScript("classpath:/nop/redis/session_set.lua");
    DigestedText HASH_PUT_EX = loadScript("classpath:/nop/redis/hash_put_ex.lua");
    DigestedText HASH_PUT_IF_ABSENT_EX = loadScript("classpath:/nop/redis/hash_put_if_absent_ex.lua");
    DigestedText HASH_GETSET_EX = loadScript("classpath:/nop/redis/hash_getset_ex.lua");
    DigestedText KV_COMPUTE_IF_ABSENT = loadScript("classpath:/nop/redis/kv_compute_if_absent.lua");
    DigestedText HASH_COMPUTE_IF_ABSENT = loadScript("classpath:/nop/redis/hash_compute_if_absent.lua");
    DigestedText HASH_GET_SET = loadScript("classpath:/nop/redis/hash_get_set.lua");
}
