/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.demo.biz;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.orm.SqlLibMapper;
import io.nop.orm.IOrmEntity;

@SqlLibMapper("/nop/demo/sql/demo.sql-lib.xml")
public interface DemoMapper {
    IOrmEntity findFirstByName(@Name("name") String name);
}
