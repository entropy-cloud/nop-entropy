/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.orm.IOrmEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.auth.service.NopAuthErrors.ARG_ACTION;
import static io.nop.auth.service.NopAuthErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_CRUD_DISABLED;
import static io.nop.biz.BizConstants.BIZ_OBJ_NAME_THIS_OBJ;

/**
 * MFA 敏感数据表通用 CRUD 写路径收口基类（A2-followup-1，D5-F1/D6-1/D3-F3 + 同族边界裁定）。
 *
 * <p><b>治理动机</b>：五张 MFA 敏感表（Setting/Credential/TrustedDevice/RoleMfaPolicy/
 * RecoveryCode）与三张同族瞬态码表（MfaChallenge/SmsCode/EmailCode——边界裁定：纳入，码表行
 * 仅由 store 组件管理，无任何合法手工建行入口）此前均为裸 {@code CrudBizModel}，暴露继承的
 * 全部 mutation：持表级 mutation 权限即可绕过专项防护（绑定状态机/proof 前置/requireAdmin/
 * 审计）直接写敏感行——D5-F1（绕过 saveMfaPolicy 校验与绑定状态机）、D6-1（伪造 deviceHash +
 * expireAt 注入 30 天登录级 MFA 豁免）、D3-F3（植入自算恢复码/复活已用码）、码表植入已知验证码
 * （第一因子旁路原语）。
 *
 * <p><b>拒绝形态裁定</b>：{@link NopException} + {@code ERR_AUTH_MFA_CRUD_DISABLED}
 * （对齐 AGENTS.md 两级错误策略的 public API 面 ErrorCode 口径；nop-credential 侧
 * {@code UnsupportedOperationException} 先例属模块内部面，此处不沿用）。被禁动作在 biz
 * schema 中仍可见但调用即拒（显式错误，非静默失败）。
 *
 * <p><b>carve-out</b>：TrustedDevice 的 {@code delete} 保留为管理端动作（子类覆写——admin
 * 校验 + 经 {@code MfaTrustedDeviceManager} 物理删除 + revoke 族审计事件）。读类动作
 * （findPage/get/batchGet 等）不受影响。
 *
 * <p><b>继承动作面</b>（{@code CrudBizModel} @BizMutation 全集，执行期经容器 biz schema 核对）：
 * save/update/delete/batchDelete/batchUpdate/batchModify/saveOrUpdate/updateByQuery/deleteByQuery/
 * copyForNew/recoverDeleted。多对多关联三动作（add/remove/updateManyToManyRelations）无需覆写：
 * 这些实体无多对多关联 prop，基类实现经 {@code requireManyToManyPropMeta} fail-closed。
 */
public abstract class MfaSensitiveTableBizModel<T extends IOrmEntity> extends CrudBizModel<T> {

    protected NopException crudWriteDisabled(String action) {
        return new NopException(ERR_AUTH_MFA_CRUD_DISABLED)
                .param(ARG_BIZ_OBJ_NAME, getBizObjName())
                .param(ARG_ACTION, action);
    }

    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public T save(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw crudWriteDisabled("save");
    }

    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public T update(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw crudWriteDisabled("update");
    }

    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        throw crudWriteDisabled("delete");
    }

    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    public Set<String> batchDelete(@Name("ids") Set<String> ids, IServiceContext context) {
        throw crudWriteDisabled("batchDelete");
    }

    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    public void batchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data,
                            @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context) {
        throw crudWriteDisabled("batchUpdate");
    }

    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    public void batchModify(@Name("data") List<Map<String, Object>> data,
                            @Optional @Name("common") Map<String, Object> common,
                            @Optional @Name("delIds") Set<String> delIds, IServiceContext context) {
        throw crudWriteDisabled("batchModify");
    }

    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public T saveOrUpdate(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw crudWriteDisabled("saveOrUpdate");
    }

    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    public int updateByQuery(@Name("query") QueryBean query, @Name("data") Map<String, Object> data,
                             IServiceContext context) {
        throw crudWriteDisabled("updateByQuery");
    }

    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    public int deleteByQuery(@Name("query") QueryBean query, IServiceContext context) {
        throw crudWriteDisabled("deleteByQuery");
    }

    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public T copyForNew(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw crudWriteDisabled("copyForNew");
    }

    /**
     * 逻辑删除恢复也是写旁路（直接 dao 写、绕过 save/delete 防线），同口径禁用。仅对配置了
     * 逻辑删除的实体可达（Setting/RecoveryCode/RoleMfaPolicy/MfaCredential），无逻辑删除的
     * 码表/TrustedDevice 由基类 {@code isAllowGetDeleted} 先行拒绝——覆写保持统一形态。
     */
    @Description("Disabled on MFA sensitive tables: use the dedicated MFA flow")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public T recoverDeleted(@Name("id") String id, IServiceContext context) {
        throw crudWriteDisabled("recoverDeleted");
    }
}
