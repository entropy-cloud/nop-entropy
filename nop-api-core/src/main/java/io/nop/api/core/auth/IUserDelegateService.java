package io.nop.api.core.auth;

import java.util.Set;

public interface IUserDelegateService {
    /**
     * 判断用户能否代理指定owner的工作
     *
     * @param userId  当前用户id
     * @param ownerId 工作所属用于的id
     */
    boolean canDelegate(String userId, String ownerId, String scope);

    /**
     * 在指定做做范围内能够代理的工作所属人的列表
     *
     * @param userId 当前用户id
     * @param scope  工作范围
     * @return ownerId的列表
     */
    Set<String> getDelegateOwnerIds(String userId, String scope);
}
