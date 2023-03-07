/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package org.beetl.sql.jmh.jpa;

import org.beetl.sql.jmh.jpa.vo.JpaSysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaUserMapper extends JpaRepository<JpaSysUser, Integer> {
    @Query(value = "select * from sys_user where id=?1 ", nativeQuery = true)
    public JpaSysUser selectById(Integer id);

    @Query(value = "select a from JpaSysUser a where a.id=:id ")
    public JpaSysUser selectTemplateById(@Param("id") Integer id);

    @Query(value = "select a  FROM JpaSysUser a WHERE a.code = :code")
    Page<JpaSysUser> pageQuery(@Param("code") String code, Pageable pageable);
}
