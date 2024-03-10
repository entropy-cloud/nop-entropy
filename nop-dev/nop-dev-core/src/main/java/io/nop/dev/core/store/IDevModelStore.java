/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dev.core.store;

import java.util.Map;

public interface IDevModelStore {
    Map<String, Object> loadModel(String path);

    void saveModel(String path, Map<String, Object> data);
}
