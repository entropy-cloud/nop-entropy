/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.api.actor;

/**
 * @author canonical_entropy@163.com
 */
public interface IWfActorResolver {
    IWfActor resolveUser(String userId);

    IWfActor resolveActor(String actorType, String actorId, String deptId);

    /**
     * 上级
     * @param actor 执行者
     */
    IWfActor getManager(IWfActor actor, int upLevel);

    /**
     * 部门负责人
     *
     * @param actor 执行者
     * @param upLevel 向上查找大部门时查找几级。1表示直属部门+1级
     */
    IWfActor getDeptManager(IWfActor actor, int upLevel);
}