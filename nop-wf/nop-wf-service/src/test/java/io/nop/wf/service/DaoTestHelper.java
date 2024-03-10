/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service;

import io.nop.auth.dao.entity.NopAuthDept;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.dao.api.DaoProvider;

public class DaoTestHelper {
    public static NopAuthUser saveUser(String userId) {
        NopAuthUser user = new NopAuthUser();
        user.setUserName("user_" + userId);
        user.setUserId(userId);
        user.setNickName(user.getUserName());
        user.setPassword("123");
        user.setOpenId(userId);
        user.setUserType(1);
        user.setStatus(1);
        user.setGender(1);
        user.setTenantId("0");
        DaoProvider.instance().daoFor(NopAuthUser.class).saveEntity(user);
        return user;
    }

    public static NopAuthDept saveDept(String deptId) {
        NopAuthDept dept = new NopAuthDept();
        dept.setDeptId(deptId);
        dept.setDeptName("dept_" + deptId);
        DaoProvider.instance().daoFor(NopAuthDept.class).saveEntity(dept);
        return dept;
    }

    public static void saveRole(String roleId) {
        NopAuthRole role = new NopAuthRole();
        role.setRoleId(roleId);
        role.setRoleName(roleId);

        DaoProvider.instance().daoFor(NopAuthRole.class).saveEntity(role);
    }

    public static void saveUserRole(String userId, String roleId) {
        NopAuthUserRole userRole = new NopAuthUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);

        DaoProvider.instance().daoFor(NopAuthUserRole.class).saveEntity(userRole);
    }
}
