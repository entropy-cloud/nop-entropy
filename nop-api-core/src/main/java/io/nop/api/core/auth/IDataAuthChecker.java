/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.auth;

import io.nop.api.core.beans.TreeBean;

/**
 * 数据权限检查
 */
public interface IDataAuthChecker {
    /**
     * 是否允许访问指定业务实体
     *
     * @param bizObj  业务对象名
     * @param action  待执行动作
     * @param entity  业务实体
     * @param context 上下文对象，可以获取到IUserContext
     * @return 是否允许该操作
     */
    boolean isPermitted(String bizObj, String action, Object entity, ISecurityContext context);

    /**
     * 为列表查询追加数据权限相关的过滤条件
     *
     * @param bizObj  业务对象名
     * @param context 上下文对象，可以获取到IUserContext
     */
    TreeBean getFilter(String bizObj, String action, ISecurityContext context);
}