/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.api.core.util.SourceLocation;

public interface OrmEqlConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(OrmEqlConfigs.class);
//    IConfigReference<Boolean> CFG_ALLOW_UNDERSCORE_NAME_IN_EQL =
//            varRef(s_loc, "nop.orm.allow-underscore-name-in-eql", Boolean.class, true);
}