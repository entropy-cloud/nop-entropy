package io.nop.demo.spring;

import io.nop.demo.spring.domain.SysUser;

public interface SysUserMapperEx extends SysUserMapper {
    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    public SysUser selectUserById(String userId);

}
