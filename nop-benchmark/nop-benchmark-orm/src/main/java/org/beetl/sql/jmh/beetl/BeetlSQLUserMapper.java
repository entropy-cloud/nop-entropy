/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package org.beetl.sql.jmh.beetl;

import org.beetl.sql.core.page.PageRequest;
import org.beetl.sql.core.page.PageResult;
import org.beetl.sql.jmh.beetl.vo.BeetlSQLSysUser;
import org.beetl.sql.mapper.BaseMapper;
import org.beetl.sql.mapper.annotation.Param;
import org.beetl.sql.mapper.annotation.Sql;
import org.beetl.sql.mapper.annotation.SqlResource;
import org.beetl.sql.mapper.annotation.Template;

@SqlResource("user")
public interface BeetlSQLUserMapper extends BaseMapper<BeetlSQLSysUser> {
    @Sql("select * from sys_user where id = ?")
    BeetlSQLSysUser selectById(Integer id);

    @Template("select * from sys_user where id = #{id}")
    BeetlSQLSysUser selectTemplateById(@Param("id") Integer id);

    /* user.md#userSelect */
    BeetlSQLSysUser userSelect(@Param("id") Integer id);

    PageResult<BeetlSQLSysUser> queryPage(@Param("code") String code, PageRequest request);
}
