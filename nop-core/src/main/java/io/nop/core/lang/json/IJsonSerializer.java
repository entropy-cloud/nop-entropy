/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json;

import io.nop.api.core.util.SourceLocation;

/**
 * 通过此接口实现自定义的json序列化
 */
public interface IJsonSerializer {
    void serializeToJson(SourceLocation loc, Object o, IJsonHandler out);
}