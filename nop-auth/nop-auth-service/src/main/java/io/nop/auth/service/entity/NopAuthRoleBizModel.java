/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleResource;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.auth.service.NopAuthConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.utils.Underscore;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.auth.service.NopAuthErrors.ARG_ROLE_ID;
import static io.nop.auth.service.NopAuthErrors.ARG_USER_ID;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_NOT_ALLOW_EDIT_INTERNAL_ROLE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_ONLY_ADMIN_CAN_ASSIGN_INTERNAL_ROLE;
import static java.util.Comparator.comparing;

@BizModel("NopAuthRole")
public class NopAuthRoleBizModel extends CrudBizModel<NopAuthRole> {
    public NopAuthRoleBizModel() {
        setEntityName(NopAuthRole.class.getName());
    }

    @Override
    protected void defaultPrepareSave(EntityData<NopAuthRole> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        this.checkAllowEdit(entityData.getEntity(), context);
    }

    @Override
    protected void defaultPrepareUpdate(EntityData<NopAuthRole> entityData, IServiceContext context) {
        super.defaultPrepareUpdate(entityData, context);
        this.checkAllowEdit(entityData.getEntity(), context);
    }

    @BizAction
    protected void checkAllowEdit(@Name("role") NopAuthRole role, IServiceContext context) {
        if (role.getRoleId().startsWith(AuthCoreConstants.NOP_ROLE_PREFIX))
            throw new NopException(ERR_AUTH_NOT_ALLOW_EDIT_INTERNAL_ROLE)
                    .param(ARG_ROLE_ID, role.getRoleId());
    }

    @BizLoader
    @GraphQLReturn(bizObjName = "NopAuthUser")
    public List<NopAuthUser> roleUsers(@ContextSource NopAuthRole role) {
        return role.getUserMappings().stream().map(NopAuthUserRole::getUser)
                .sorted(comparing(NopAuthUser::getUserName)).collect(Collectors.toList());
    }

    @BizMutation
    public void removeRoleUsers(@Name("roleId") String roleId,
                                @Name("userIds") Collection<String> userIds) {
        removeRelations(NopAuthUserRole.class.getName(),
                "roleId", "userId",
                roleId, userIds, null);
    }

    @BizMutation
    public void addRoleUsers(@Name("roleId") String roleId, @Name("userIds") Collection<String> userIds,
                             IServiceContext context) {
        if (userIds != null && roleId != null) {
            for (String userId : userIds) {
                checkAllowAssignRole(roleId, userId, context);
            }
        }
        addRelations(NopAuthUserRole.class.getName(), "roleId", "userId",
                roleId, userIds, null);
    }

    @BizAction
    protected void checkAllowAssignRole(@Name("roleId") String roleId, @Name("userId") String userId, IServiceContext context) {
        if (roleId.startsWith(AuthCoreConstants.NOP_ROLE_PREFIX)) {
            if (!context.getUserContext().isUserInRole(AuthCoreConstants.ROLE_NOP_ADMIN))
                throw new NopException(ERR_AUTH_ONLY_ADMIN_CAN_ASSIGN_INTERNAL_ROLE)
                        .param(ARG_ROLE_ID, roleId).param(ARG_USER_ID, userId);
        }
    }

    @BizLoader
    public List<String> roleResourceIds(@ContextSource NopAuthRole role,
                                        IServiceContext context) {
        return (List) Underscore.pluck(role.getResourceMappings(), "resourceId");
    }

    @BizMutation
    public void updateRoleResources(@Name("roleId") String roleId,
                                    @Name("siteId") String siteId,
                                    @Name("resourceIds") Collection<String> resourceIds,
                                    @Optional @Name("filter") TreeBean filter) {
        if (StringHelper.isEmpty(siteId))
            siteId = NopAuthConstants.SITE_ID_MAIN;

        Map<String, Object> fixedProps = new HashMap<>();
        fixedProps.put("roleId", roleId);

        filter = FilterBeans.and(filter, FilterBeans.eq("resource.siteId", siteId));
        super.updateRelationsEx(NopAuthRoleResource.class.getName(), "roleId", fixedProps, filter,
                true, "resourceId", resourceIds);
    }
}