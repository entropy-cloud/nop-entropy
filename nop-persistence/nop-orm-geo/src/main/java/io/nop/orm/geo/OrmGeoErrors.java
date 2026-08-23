/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface OrmGeoErrors {
    String ARG_CLASS_NAME = "className";

    ErrorCode ERR_ORM_GEO_INVALID_GEOMETRY_OBJECT = define("nop.err.orm.geo.invalid-geometry-object",
            "Value of type [{className}] cannot be converted to a geometry object", ARG_CLASS_NAME);
}
