
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.password.IPasswordPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.generator.IUserIdGenerator;
import io.nop.auth.service.NopAuthConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.core.context.IServiceContext;
import io.nop.dao.DaoConstants;

import javax.inject.Inject;

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

        String salt = passwordEncoder.generateSalt();
        String password = passwordEncoder.encodePassword(salt, user.getPassword());
        user.setSalt(salt);
        user.setPassword(password);

        user.setSid(userIdGenerator.generateUserId(user));
        user.setOpenId(userIdGenerator.generateUserOpenId(user));

        user.setStatus(NopAuthConstants.USER_STATUS_ACTIVE);
        user.setDelFlag(DaoConstants.NO_VALUE);
    }

    @Description("修改指定用户的密码")
    @BizMutation
    public void changeUserPassword(@Name("userId") String userId,
                                   @Name("password") String password,
                                   IServiceContext context) {
        NopAuthUser user = this.get(userId, false, context);
        passwordPolicy.checkAllowedPassword(userId, password);

        String salt = passwordEncoder.generateSalt();
        password = passwordEncoder.encodePassword(salt, password);
        user.setSalt(salt);
        user.setPassword(password);
    }

    @Description("修改自己的密码")
    @BizMutation
    public void changeSelfPassword(@Name("oldPassword") String oldPassword,
                                   @Name("newPassword") String newPassword) {
        IUserContext userContext = IUserContext.get();
        if (userContext == null)
            throw new NopException(ERR_AUTH_USER_NOT_LOGIN);

        passwordPolicy.checkAllowedPassword(userContext.getUserId(), newPassword);

        NopAuthUser user = dao().requireEntityById(userContext.getUserId());
        if (!passwordEncoder.passwordMatches(user.getSalt(), oldPassword, user.getPassword())) {
            throw new NopException(ERR_AUTH_OLD_PASSWORD_NOT_MATCH);
        }

        String salt = passwordEncoder.generateSalt();
        String password = passwordEncoder.encodePassword(salt, newPassword);
        user.setSalt(salt);
        user.setPassword(password);
    }
}