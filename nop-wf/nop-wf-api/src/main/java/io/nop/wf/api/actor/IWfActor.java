/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.api.actor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Objects;

/**
 * @author canonical_entropy@163.com
 */
public interface IWfActor {
    /**
     * 系统本身对应的用户id
     */
    String SYS_USER_ID = "0";

    String ACTOR_TYPE_USER = "user";
    String ACTOR_TYPE_DEPT = "dept";
    String ACTOR_TYPE_ROLE = "role";

    String ACTOR_TYPE_GROUP = "group";

    /**
     * 自动执行任务可能将任务分配到executor
     */
    String ACTOR_TYPE_EXECUTOR = "executor";

    String getActorType();

    String getActorId();

    @JsonIgnore
    default String getActorKey() {
        return getActorType().equals(ACTOR_TYPE_USER) ? getActorId() : getActorType() + ":" + getActorId();
    }

    String getActorName();

    /**
     * 限定在某个部门范围内
     *
     * @return
     */
    String getDeptId();

    /**
     * 得到actor对应的用户列表
     *
     * @return
     */
    @Nonnull
    List<? extends IWfActor> getUsers();

    /**
     * 判断actor的getUsers()集合中是否包含指定用户
     *
     * @param userId
     * @return
     */
    default boolean containsUser(String userId) {
        if (ACTOR_TYPE_USER.equals(getActorType()))
            return getActorId().equals(userId);

        List<? extends IWfActor> users = getUsers();
        for (IWfActor user : users) {
            if (user.getActorId().equals(userId))
                return true;
        }
        return false;
    }

    // 不应包含getDefaultUser这种涉及到复杂选择逻辑的方法

    default boolean isSame(IWfActor actor) {
        return getActorType().equals(actor.getActorType()) && getActorId().equals(actor.getActorId()) && Objects.equals(getDeptId(),
                actor.getDeptId());
    }

    default boolean isActor(String actorType, String actorId, String actorDeptId) {
        return getActorType().equals(actorType) && getActorId().equals(actorId) && Objects.equals(getDeptId(), actorDeptId);
    }
}