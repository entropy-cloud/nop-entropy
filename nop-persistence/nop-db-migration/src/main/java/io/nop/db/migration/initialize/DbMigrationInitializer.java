/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.initialize;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;

public class DbMigrationInitializer implements ICoreInitializer {

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_REFLECTION;
    }

    @Override
    public void initialize() {
    }
}
