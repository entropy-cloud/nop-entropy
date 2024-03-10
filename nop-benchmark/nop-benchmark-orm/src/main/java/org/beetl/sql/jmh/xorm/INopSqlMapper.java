/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh.xorm;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.orm.SqlLibMapper;
import org.beetl.sql.jmh.xorm.vo.SysUser;

@SqlLibMapper("/nop/test/sql/test.sql-lib.xml")
public interface INopSqlMapper {
    SysUser selectById(@Name("id") Integer id);

    SysUser selectTemplateById(@Name("id") Integer id);

    SysUser userSelect(@Name("id") Integer id);
}
