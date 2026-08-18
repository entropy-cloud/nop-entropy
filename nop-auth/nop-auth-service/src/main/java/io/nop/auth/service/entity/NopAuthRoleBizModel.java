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
import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.biz.INopAuthRoleBiz;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;
import io.nop.auth.dao.entity.NopAuthRoleResource;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.NopAuthErrors;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.utils.Underscore;
import io.nop.dao.api.IEntityDao;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.auth.service.NopAuthErrors.ARG_MFA_LEVEL;
import static io.nop.auth.service.NopAuthErrors.ARG_ROLE_ID;
import static io.nop.auth.service.NopAuthErrors.ARG_USER_ID;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_NOT_ALLOW_EDIT_INTERNAL_ROLE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_ONLY_ADMIN_CAN_ASSIGN_INTERNAL_ROLE;
import static java.util.Comparator.comparing;

@BizModel("NopAuthRole")
public class NopAuthRoleBizModel extends CrudBizModel<NopAuthRole> implements INopAuthRoleBiz {
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
    @Override
    protected void defaultPrepareDelete(@Name("entity") NopAuthRole entity, IServiceContext context) {
        super.defaultPrepareDelete(entity, context);
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

    // ===================== 角色级 MFA 强制策略管理（设计 §4.3，W13-impl） =====================

    @Inject
    @Nullable
    protected IAuditService auditService;

    /**
     * 保存/更新角色的 MFA 强制策略（1:1 按需建行；无行 = 该角色无策略）。
     * <p>
     * 策略是独立实体而非角色字段；admin 权限走运行时 {@code requireAdmin} 校验
     * （{@code NopAuthUserBizModel.resetUserMfa} 先例模式，非注解模式）；策略变更经
     * {@link IAuditService#saveAudit} 落 NopAuthOpLog（@BizAudit 为装饰性注解，W12 裁定）。
     * 登录时评估——策略变更在下次登录生效（权限快照语义）。
     */
    @BizMutation
    public void saveMfaPolicy(@Name("roleId") String roleId,
                              @Name("minMfaLevel") int minMfaLevel,
                              @Optional @Name("allowTrustedDevice") Boolean allowTrustedDevice,
                              IServiceContext context) {
        requireAdmin(context);
        if (minMfaLevel < 1 || minMfaLevel > 3) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_ROLE_ID, roleId)
                    .param(ARG_MFA_LEVEL, minMfaLevel)
                    .param("msg", "minMfaLevel must be 1/2/3 (channel-otp/totp/webauthn)");
        }

        IEntityDao<NopAuthRoleMfaPolicy> dao = daoFor(NopAuthRoleMfaPolicy.class);
        NopAuthRoleMfaPolicy policy = dao.getEntityById(roleId);
        boolean created = false;
        if (policy == null) {
            policy = dao.newEntity();
            policy.setRoleId(roleId);
            created = true;
        } else if (policy.getDelFlag() != null && policy.getDelFlag() != 0) {
            // 逻辑删除行（removeMfaPolicy 后重建）：复活（delFlag 归零）
            policy.setDelFlag((byte) 0);
        }
        policy.setMinMfaLevel(minMfaLevel);
        policy.setAllowTrustedDevice(allowTrustedDevice == null || allowTrustedDevice ? (byte) 1 : (byte) 0);
        if (created) {
            dao.saveEntity(policy);
        } else {
            dao.updateEntityDirectly(policy);
        }

        auditPolicyChange(context, "NopAuthRole__saveMfaPolicy",
                created ? "mfa-policy-created" : "mfa-policy-updated",
                roleId, minMfaLevel, allowTrustedDevice);
    }

    /**
     * 撤销角色的 MFA 强制策略（删行即撤策略——单态，无 status 双态，设计 §4.1 结论 8）。
     * 对无策略角色的移除是幂等的（显式 no-op，不抛错）。
     */
    @BizMutation
    public void removeMfaPolicy(@Name("roleId") String roleId, IServiceContext context) {
        requireAdmin(context);
        IEntityDao<NopAuthRoleMfaPolicy> dao = daoFor(NopAuthRoleMfaPolicy.class);
        NopAuthRoleMfaPolicy policy = dao.getEntityById(roleId);
        if (policy != null) {
            dao.deleteEntity(policy);
            auditPolicyChange(context, "NopAuthRole__removeMfaPolicy", "mfa-policy-removed",
                    roleId, null, null);
        }
    }

    /** 运行时 admin 角色校验（{@code NopAuthUserBizModel.requireAdmin} 先例模式）。 */
    private void requireAdmin(IServiceContext context) {
        io.nop.api.core.auth.IUserContext userContext = context.getUserContext();
        if (userContext == null) {
            throw new NopException(io.nop.auth.core.AuthCoreErrors.ERR_AUTH_USER_NOT_LOGIN);
        }
        Set<String> roles = userContext.getRoles();
        if (roles == null || (!roles.contains(NopAuthConstants.ROLE_ADMIN)
                && !roles.contains(NopAuthConstants.ROLE_NOP_ADMIN))) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userContext.getUserId())
                    .param("msg", "only admin can manage role MFA policies");
        }
    }

    /** 策略变更审计事件（saveMfaPolicy/removeMfaPolicy），落 NopAuthOpLog。 */
    private void auditPolicyChange(IServiceContext context, String operation, String event, String roleId,
                                   Integer minMfaLevel, Boolean allowTrustedDevice) {
        if (auditService == null)
            return;
        AuditRequest audit = new AuditRequest();
        audit.setOperation(operation);
        audit.setDescription("role-mfa-policy:" + event);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        io.nop.api.core.auth.IUserContext uc = context.getUserContext();
        if (uc != null) {
            audit.setUserId(uc.getUserId());
            audit.setUserName(uc.getUserName());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", event);
        data.put("roleId", roleId);
        if (minMfaLevel != null)
            data.put("minMfaLevel", minMfaLevel);
        if (allowTrustedDevice != null)
            data.put("allowTrustedDevice", allowTrustedDevice);
        audit.setRequestData(JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }
}