/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.api;

public class DaoProvider {
    private static IDaoProvider _instance;

    public static IDaoProvider instance() {
        return _instance;
    }

    public static void registerInstance(IDaoProvider daoProvider) {
        _instance = daoProvider;
    }
}
