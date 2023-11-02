/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizAudit;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.password.IPasswordPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.generator.IUserIdGenerator;
import io.nop.auth.service.NopAuthConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.core.context.IServiceContext;
import io.nop.dao.DaoConstants;
import jakarta.inject.Inject;

import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_OLD_PASSWORD_NOT_MATCH;
import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_USER_NOT_LOGIN;

@BizModel("NopAuthUser")
@Locale("zh-CN")
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> {

    @Inject
    IPasswordEncoder passwordEncoder;

    @Inject
    IUserIdGenerator userIdGenerator;

    @Inject
    IPasswordPolicy passwordPolicy;

    public NopAuthUserBizModel() {
        setEntityName(NopAuthUser.class.getName());
    }

    @BizAction
    @Override
    protected void defaultPrepareSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        NopAuthUser user = entityData.getEntity();
        passwordPolicy.checkAllowedPassword(user.getUserName(), user.getPassword());

        String salt = passwordEncoder.generateSalt();
        String password = passwordEncoder.encodePassword(salt, user.getPassword());
        user.setSalt(salt);
        user.setPassword(password);

        user.setUserId(userIdGenerator.generateUserId(user));
        user.setOpenId(userIdGenerator.generateUserOpenId(user));

        if (user.getTenantId() == null) {
            String tenantId = ContextProvider.currentTenantId();
            if (tenantId == null)
                tenantId = DaoConstants.DEFAULT_TENANT_ID;
            user.setTenantId(tenantId);
        }

        user.setStatus(NopAuthConstants.USER_STATUS_ACTIVE);
        user.setDelFlag(DaoConstants.NO_VALUE);
    }

    @Description("@i18n:common.resetUserPassword")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    public void resetUserPassword(@Name("userId") String userId,
                                  @Name("password") String password,
                                  IServiceContext context) {
        NopAuthUser user = this.get(userId, false, context);
        passwordPolicy.checkAllowedPassword(user.getUserName(), password);

        String salt = passwordEncoder.generateSalt();
        password = passwordEncoder.encodePassword(salt, password);
        user.setSalt(salt);
        user.setPassword(password);
    }

    @Description("修改自己的密码")
    @BizMutation
    @BizAudit
    public void changeSelfPassword(@Name("oldPassword") String oldPassword,
                                   @Name("newPassword") String newPassword, IServiceContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null)
            throw new NopException(ERR_AUTH_USER_NOT_LOGIN);

        passwordPolicy.checkAllowedPassword(userContext.getUserName(), newPassword);

        NopAuthUser user = dao().requireEntityById(userContext.getUserId());
        if (!passwordEncoder.passwordMatches(user.getSalt(), oldPassword, user.getPassword())) {
            throw new NopException(ERR_AUTH_OLD_PASSWORD_NOT_MATCH);
        }

        String salt = passwordEncoder.generateSalt();
        String password = passwordEncoder.encodePassword(salt, newPassword);
        user.setSalt(salt);
        user.setPassword(password);
    }

    @Description("@i18n:common.enableUser")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    public void enableUser(@Name("userId") String userId,
                           IServiceContext context) {
        NopAuthUser user = this.get(userId, false, context);
        if (user.getStatus() != AuthApiConstants.USER_STATUS_ACTIVE) {
            user.setStatus(AuthApiConstants.USER_STATUS_ACTIVE);
        }
    }

    @Description("@i18n:common.disableUser")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    public void disableUser(@Name("userId") String userId,
                            IServiceContext context) {
        NopAuthUser user = this.get(userId, false, context);
        if (user.getStatus() == AuthApiConstants.USER_STATUS_ACTIVE) {
            user.setStatus(AuthApiConstants.USER_STATUS_DISABLED);
        }
    }
}