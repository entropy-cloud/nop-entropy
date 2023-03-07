/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.source;

import io.nop.api.core.util.IOrdered;

public interface IConfigSourceLoader extends IOrdered {
    default boolean isEnabled() {
        return true;
    }

    IConfigSource loadConfigSource(IConfigSource currentConfig);
}
