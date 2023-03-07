/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package org.beetl.sql.jmh.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.beetl.sql.jmh.mybatis.vo.MyBatisSysCustomerView;
import org.beetl.sql.jmh.mybatis.vo.MyBatisSysUser;

public interface MyBatisUserRepository extends BaseMapper<MyBatisSysUser> {
    @Select("select * from sys_user where id = #{id}")
    public MyBatisSysUser selectEntityById(@Param("id") Integer id);

    public MyBatisSysUser selectUser(@Param("id") Integer id);

    public MyBatisSysCustomerView selectView(@Param("id") Integer id);

}
