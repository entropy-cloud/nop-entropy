/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.entity;

import io.nop.api.core.annotations.biz.BizArgsNormalizer;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.exceptions.UnknownEntityException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import io.nop.credential.api.registry.CredentialType;
import io.nop.credential.api.registry.ICredentialTypeRegistry;
import io.nop.credential.biz.INopCredentialBiz;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.crypto.CredentialErrors;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialAuth;
import io.nop.credential.dao.entity.NopCredentialUsage;
import io.nop.credential.service.CredentialOwnership;
import io.nop.credential.service.CredentialProviderImpl;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.biz.BizConstants.BEAN_nopQueryBeanArgsNormalizer;
import static io.nop.biz.BizConstants.BIZ_OBJ_NAME_THIS_OBJ;

/**
 * {@code NopCredential} 的管理 CRUD BizModel。
 *
 * <p><b>结构性明文边界</b>：xmeta 中 {@code data} 字段为 {@code published="false"}，因此标准 GraphQL
 * schema 不暴露密文。本类在此基础上做三重防御：
 * <ol>
 *   <li>继承的标准 {@code save} 被覆盖为抛出异常，强制走自定义 {@link #saveCredential} 路径（明文输入唯一入口）</li>
 *   <li>{@code get} / {@code findPage} / {@code findList} / {@code findFirst} / {@code batchGet}
 *       在返回前强制 {@code entity.setData(null)} + 会话驱逐（即使 xmeta 边界被绕过也无密文，
 *       D1-01 补齐继承查询动作面）</li>
 *   <li>{@link #saveCredential} 内部加密后持久化，返回的实体 {@code data} 已置 null</li>
 * </ol>
 *
 * <p><b>W11 归属两层防御</b>（设计 §5.3）：读类动作经 {@link #defaultPrepareQuery} 注入结构性过滤
 * （非管理员可见「system 级（含存量 NULL）∨ 自己的 user 级」）；写类动作（saveCredential 修改路径/
 * delete）前置分级（system=管理员，user=owner+管理员；无登录态的内部调用按一期行为放行 system 级、
 * 拒绝 user 级）；单条越权访问（get/delete/maskList/test 及 saveCredential 更新路径——D1-03/D4-07
 * 写读路径口径对称）归一"不存在"语义（防 credentialId 枚举探测归属）。
 * provider 层 per-method 归属校验为纵深防御（BizModel 面被绕过时第二道 fail-closed）。
 *
 * <p><b>继承动作面收口（W11）</b>：标准 {@code update}/{@code batchDelete}/{@code updateByQuery}/
 * {@code deleteByQuery}/{@code copyForNew} 禁用（抛 {@link UnsupportedOperationException}，与标准
 * {@code save} 同口径——分别堵直写密文/批量绕过/prepareQuery 旁路/引用计数绕过/复制密文行）；
 * {@code batchGet} 改走行级可见性过滤语义。{@code reencryptAll} 限管理员。
 *
 * <p>其他自定义 action：{@link #maskList}、{@link #typeList}、{@link #test}、{@link #reencryptAll}。
 */
@BizModel("NopCredential")
@Locale("zh-CN")
public class NopCredentialBizModel extends CrudBizModel<NopCredential> implements INopCredentialBiz {

    /**
     * 凭证密文编解码器。字段为 protected 以兼容 NopIoC 字段注入。
     */
    @Inject
    protected CredentialCipher credentialCipher;

    @Inject
    protected ICredentialTypeRegistry credentialTypeRegistry;

    @Inject
    protected ICredentialProvider credentialProvider;

    /**
     * 引擎内部通道（分组写用）。注入实现类——{@code engineUpdateInLock} 不在 SPI 面上。
     */
    @Inject
    protected CredentialProviderImpl credentialProviderImpl;

    // 注意：IDaoProvider daoProvider 由父类 CrudBizModel 声明并通过 NopIoC 注入，
    // 此处不再重复声明（重复声明会导致字段遮蔽，子类字段保持 null）。
    // ICredentialKeyProvider 用于 reencryptAll 读取 active keyId。
    @Inject
    protected ICredentialKeyProvider keyProvider;

    public NopCredentialBizModel() {
        setEntityName(NopCredential.class.getName());
    }

    // ==================== 明文边界：标准 save 被禁用 ====================

    /**
     * 禁用继承的标准 {@code save}，强制走 {@link #saveCredential} 路径。
     *
     * <p>原因：标准 save 通过 {@code data} map 接收实体字段，而 {@code data} 字段在 xmeta 中为
     * {@code published="false"}，无法通过 GraphQL 输入接收明文。允许标准 save 还会让调用方
     * 绕过加密（直接写入未加密的 data）。
     */
    @Description("禁用标准 save，请使用 saveCredential")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredential save(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("use saveCredential action instead");
    }

    // ==================== 明文输入唯一入口：saveCredential ====================

    /**
     * 凭证明文输入唯一入口。{@code fields} 为明文字段 map（例如 {@code {"apiKey":"sk-xxx"}}），
     * 内部序列化为 JSON、加密为 {@code cv1:} 密文后持久化到 {@code data} 字段。
     *
     * <p><b>W9 分组写语义（authType=oauth2，设计 §3.3）</b>：人工字段集合整包替换（未传即删）
     * 不变，但<b>引擎保留字段不动</b>（锁下从当前 data 解出 token 集合并合并回写）；输入出现
     * 保留字段名（accessToken/refreshToken/expiresAt/tokenType/scope）一律拒绝（防人工伪造
     * token 破坏刷新状态机）。更新路径与惰性刷新共用同一行锁串行化入口（互斥覆盖
     * "刷新 vs 人工保存"）。非 oauth2 类型维持一期整包覆盖语义（零变更）。
     *
     * <p><b>W11 归属输入（设计 §5.3，可选参数）</b>：{@code scope}/{@code ownerId} 均可不传——
     * 创建缺省 = system（一期行为不变）；更新缺省 = 保持不变。规则：scope 取值 system|user；
     * system 级 ownerId 强制空、创建限管理员（无登录态内部调用按一期行为放行）；user 级
     * ownerId 必填——普通用户强制等于当前登录用户（不可指定他人），管理员可代建（指定他人
     * owner，审计载体 = 行内 createdBy ≠ ownerId）；归属不可变（显式传入与存量不符即拒，
     * "转让" = 删旧建新）。写分级：system 级限管理员、user 级限 owner+管理员。
     *
     * <p>返回的实体 {@code data} 已置 null（不向调用方暴露密文）。
     *
     * @param typeName 凭证类型名（必须已注册，否则 fail-closed）
     * @param name     凭证显示名
     * @param fields   明文字段 map（必填、非空）
     * @param id       可选；提供则更新已存在凭证，不提供则新建
     * @param scope    可选；凭证归属 system|user（创建缺省 system，更新缺省保持不变）
     * @param ownerId  可选；user 级归属用户 userId（system 级必须为空）
     */
    @Description("保存凭证（明文输入路径，内部加密后持久化）")
    @BizMutation
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredential saveCredential(
            @Name("typeName") String typeName,
            @Name("name") String name,
            @Name("fields") Map<String, Object> fields,
            @Optional @Name("id") String id,
            @Optional @Name("scope") String scope,
            @Optional @Name("ownerId") String ownerId,
            IServiceContext context) {

        // fail-closed: 类型必须已知
        if (StringHelper.isEmpty(typeName)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_UNKNOWN_TYPE)
                    .param(CredentialErrors.ARG_TYPE_NAME, typeName);
        }
        CredentialType type = credentialTypeRegistry.getType(typeName); // throws ERR_CREDENTIAL_UNKNOWN_TYPE

        if (StringHelper.isEmpty(name)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_NAME_REQUIRED)
                    .param(CredentialErrors.ARG_TYPE_NAME, typeName);
        }

        if (fields == null || fields.isEmpty()) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_FIELDS_REQUIRED)
                    .param(CredentialErrors.ARG_TYPE_NAME, typeName);
        }

        // W11 归属输入归一化：空串视同未传（一期调用不传 scope/ownerId = system，零迁移）
        scope = CredentialOwnership.normalizeEmptyToNull(scope);
        ownerId = CredentialOwnership.normalizeEmptyToNull(ownerId);
        if (scope != null && !CredentialOwnership.isValidScope(scope)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_INVALID_SCOPE)
                    .param(CredentialErrors.ARG_SCOPE, scope);
        }

        // W9: oauth2 类型保留字段名拒绝（token 集只能由引擎写入，防伪造破坏刷新状态机）
        if (type.isOauth2Type()) {
            List<String> reservedUsed = new ArrayList<>();
            for (String fieldName : fields.keySet()) {
                if (CredentialType.OAUTH_RESERVED_FIELD_NAMES.contains(fieldName)) {
                    reservedUsed.add(fieldName);
                }
            }
            if (!reservedUsed.isEmpty()) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_RESERVED_FIELD_INPUT)
                        .param(CredentialErrors.ARG_FIELD_NAMES, reservedUsed);
            }
        }

        IEntityDao<NopCredential> dao = dao();
        NopCredential entity;
        boolean isNew = StringHelper.isEmpty(id);
        if (isNew) {
            // W11 创建路径归属裁定（设计 §5.3）
            String effectiveScope = scope == null ? CredentialOwnership.SCOPE_SYSTEM : scope;
            String effectiveOwnerId = null;
            IUserContext userContext = IUserContext.get();
            boolean hasLogin = CredentialOwnership.hasLoginUser(userContext);
            boolean admin = CredentialOwnership.isAdmin(userContext);
            if (CredentialOwnership.SCOPE_USER.equals(effectiveScope)) {
                if (!hasLogin) {
                    // 无登录态（后台任务/服务间调用）无法确定 owner：拒绝创建 user 级
                    throw new NopException(CredentialErrors.ERR_CREDENTIAL_OWNER_REQUIRED)
                            .param(CredentialErrors.ARG_CREDENTIAL_ID, "");
                }
                if (admin) {
                    // 管理员代建：ownerId 必填（可指定他人；审计载体 = 行内 createdBy ≠ ownerId）
                    if (ownerId == null) {
                        throw new NopException(CredentialErrors.ERR_CREDENTIAL_OWNER_REQUIRED)
                                .param(CredentialErrors.ARG_CREDENTIAL_ID, "");
                    }
                    effectiveOwnerId = ownerId;
                } else {
                    // 普通用户建 user 级：ownerId 强制等于当前登录用户（不可指定他人）
                    effectiveOwnerId = userContext.getUserId();
                }
            } else {
                if (ownerId != null) {
                    throw new NopException(CredentialErrors.ERR_CREDENTIAL_OWNER_NOT_ALLOWED)
                            .param(CredentialErrors.ARG_CREDENTIAL_ID, "");
                }
                if (hasLogin && !admin) {
                    throw new NopException(CredentialErrors.ERR_CREDENTIAL_ADMIN_REQUIRED)
                            .param(CredentialErrors.ARG_CREDENTIAL_ID, "")
                            .param(CredentialErrors.ARG_REQUIRED_ROLES,
                                    CredentialOwnership.adminRolesAsString(CredentialOwnership.adminRoles()));
                }
                // 无登录态内部调用：维持一期行为（system 级可建）
            }

            entity = dao.newEntity();
            entity.setCredentialId(StringHelper.generateUUID());
            entity.setDelFlag((byte) 0);
            entity.setVersion(1);
            entity.setCreateTime(new Timestamp(System.currentTimeMillis()));
            entity.setScope(effectiveScope);
            entity.setOwnerId(effectiveOwnerId);

            // 新建：保留字段已被拒绝出现，直接整包加密（一期路径不变）
            entity.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
        } else {
            entity = dao.getEntityById(id);
            if (entity == null) {
                // D1-03/D4-07（A1-audit successor，2026-08-17）：更新目标不存在与越权拒绝统一
                // UnknownEntityException（对齐 get/delete 单条资源访问先例，防 credentialId
                // 枚举探测归属/存在性；原 ERR_CREDENTIAL_NOT_FOUND 一并归一）
                throw new UnknownEntityException(getEntityName(), id);
            }

            // W11 写分级 → D1-03/D4-07 归一：越权（system 级非管理员 / user 级非 owner）
            // 与"不存在"不可区分（三态归一；ARG_OWNER_ID 等 param 不进入对外可达异常）
            if (CredentialOwnership.writeDenialReason(
                    IUserContext.get(), entity.getScope(), entity.getOwnerId()) != null) {
                throw new UnknownEntityException(getEntityName(), id);
            }

            // D1-02/D4-05（A1-audit successor，2026-08-17）：非 oauth2 更新路径 delFlag
            // fail-closed（墓碑行拒绝改写）——与 oauth2 分支经 engineUpdateInLock probe 的
            // delFlag 检查同口径（此前仅 oauth2 分支有检查，两分支不一致）。放置于越权归一
            // 之后：越权者对墓碑行同样只看到"不存在"，不泄露删除状态
            if (entity.getDelFlag() != null && entity.getDelFlag() != 0) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_DELETED)
                        .param(CredentialErrors.ARG_CREDENTIAL_ID, id);
            }

            // W11 归属不可变：显式传入且与存量不符即拒（缺省不传 = 保持不变）
            applyImmutableOwnershipInput(entity, scope, ownerId);

            // W9 分组写（oauth2）：元数据与 data 在同一行锁 UPDATE 内提交（锁下从当前 data
            // 解出保留字段合并，人工字段整包替换；与惰性刷新互斥串行化，无两步乐观锁竞争）
            if (type.isOauth2Type()) {
                Map<String, Object> inputFields = fields;
                credentialProviderImpl.engineUpdateInLock(id,
                        current -> {
                            Map<String, Object> merged = new java.util.LinkedHashMap<>();
                            // 保留字段：从当前 data 原样保留
                            for (String reserved : CredentialType.OAUTH_RESERVED_FIELD_NAMES) {
                                if (current.containsKey(reserved)) {
                                    merged.put(reserved, current.get(reserved));
                                }
                            }
                            // 人工字段：整包替换（未传即删）
                            merged.putAll(inputFields);
                            return merged;
                        },
                        (entityLocked, updatedFields) -> {
                            entityLocked.setName(name);
                            entityLocked.setTypeName(typeName);
                            if (StringHelper.isEmpty(entityLocked.getStatus())) {
                                entityLocked.setStatus("enabled");
                            }
                        });

                // 返回前重读 + 驱逐 + 清密文（明文边界）
                entity = dao.getEntityById(id);
                orm().requireSession().evict(entity);
                entity.setData(null);
                return entity;
            }

            entity.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
        }

        entity.setTypeName(typeName);
        entity.setName(name);
        if (StringHelper.isEmpty(entity.getStatus())) {
            entity.setStatus("enabled");
        }
        entity.setUpdateTime(new Timestamp(System.currentTimeMillis()));

        if (isNew) {
            dao.saveEntityDirectly(entity);
        } else {
            dao.updateEntityDirectly(entity);
        }

        // 防御性驱逐：BizMutation 运行在读写事务中，若不驱逐则在事务 commit 时
        // 会把下面的 setData(null) 回写数据库（污染密文）。驱逐后实体变为 detached，
        // 后续修改不会触发 flush。
        orm().requireSession().evict(entity);

        // 返回前清除密文（明文边界：API 永不暴露密文）
        entity.setData(null);
        return entity;
    }

    // ==================== 明文边界 + 归属可见性：get / findPage ====================

    @Description("@i18n:biz.get|根据id获取单条数据")
    @BizQuery
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredential get(@Name("id") String id,
                             @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                             IServiceContext context) {
        NopCredential entity = super.get(id, ignoreUnknown, context);
        if (entity != null) {
            // W11 单条越权归一"不存在"（与软删除同口径，防 credentialId 枚举探测归属）
            IUserContext userContext = IUserContext.get();
            if (!CredentialOwnership.canSee(userContext, entity.getScope(), entity.getOwnerId())) {
                if (ignoreUnknown) {
                    return null;
                }
                throw new UnknownEntityException(getEntityName(), id);
            }
            // 防御性驱逐：避免 setData(null) 被事务 flush 回写数据库
            orm().requireSession().evict(entity);
            entity.setData(null);
        }
        return entity;
    }

    @Description("@i18n:biz.findPage|分页查询")
    @BizQuery
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public PageBean<NopCredential> findPage(
            @Optional @Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
            FieldSelectionBean selection, IServiceContext context) {
        PageBean<NopCredential> page = super.findPage(query, selection, context);
        if (page != null && page.getItems() != null) {
            // 防御性驱逐：避免 setData(null) 被事务 flush 回写数据库（污染密文）
            for (NopCredential entity : page.getItems()) {
                orm().requireSession().evict(entity);
                entity.setData(null);
            }
        }
        return page;
    }

    /**
     * D1-01（A1-audit successor，2026-08-17）：继承查询动作 {@code findList} 补齐
     * {@code data} 置空 + 会话驱逐（与 {@code get}/{@code findPage} 同口径——逐行防御性
     * 驱逐，确保 BizMutation 事务 flush 不把 setData(null) 回写数据库污染密文）。
     * 可见性过滤经 {@link #defaultPrepareQuery} 结构性注入（继承面，与 findPage 同源）。
     */
    @Description("@i18n:biz.findList|根据查询条件返回列表数据。与findPage的不同在于,findPage返回PageBean类型，支持分页，而这个函数返回List类型，而且缺省不分页")
    @BizQuery
    @BizArgsNormalizer(BEAN_nopQueryBeanArgsNormalizer)
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public List<NopCredential> findList(
            @Optional @Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
            FieldSelectionBean selection, IServiceContext context) {
        List<NopCredential> list = super.findList(query, selection, context);
        if (list != null) {
            for (NopCredential entity : list) {
                orm().requireSession().evict(entity);
                entity.setData(null);
            }
        }
        return list;
    }

    /**
     * D1-01：继承查询动作 {@code findFirst} 补齐 {@code data} 置空 + 会话驱逐（同上口径）。
     */
    @Description("@i18n:biz.findFirst|返回符合条件的第一条数据")
    @BizQuery
    @BizArgsNormalizer(BEAN_nopQueryBeanArgsNormalizer)
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredential findFirst(
            @Optional @Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
            FieldSelectionBean selection, IServiceContext context) {
        NopCredential entity = super.findFirst(query, selection, context);
        if (entity != null) {
            orm().requireSession().evict(entity);
            entity.setData(null);
        }
        return entity;
    }

    // ==================== W11 归属过滤与分级（设计 §5.3） ====================

    /**
     * 读类结构性过滤（{@code findPage}/{@code findList}/{@code findFirst}/{@code findCount} 统一经
     * {@code invokeDefaultPrepareQuery} 调用本方法）：非管理员登录用户限定
     * 「scope=system ∨ scope IS NULL ∨ (scope=user ∧ ownerId=本人)」（NULL 分支防存量行从普通
     * 用户视野消失）；管理员不加过滤；无登录态（内部调用）不过滤（一期行为，provider 层仍
     * fail-closed 明文出口）。归属过滤为结构性规则（硬编码语义），不做可配置数据权限。
     */
    @Override
    protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
        IUserContext userContext = IUserContext.get();
        if (!CredentialOwnership.hasLoginUser(userContext) || CredentialOwnership.isAdmin(userContext)) {
            return;
        }
        String userId = userContext.getUserId();
        query.addFilter(FilterBeans.or(
                FilterBeans.or(
                        FilterBeans.eq(NopCredential.PROP_NAME_scope, CredentialOwnership.SCOPE_SYSTEM),
                        FilterBeans.isNull(NopCredential.PROP_NAME_scope)),
                FilterBeans.and(
                        FilterBeans.eq(NopCredential.PROP_NAME_scope, CredentialOwnership.SCOPE_USER),
                        FilterBeans.eq(NopCredential.PROP_NAME_ownerId, userId))));
    }

    /**
     * 写分级（设计 §5.3 写类矩阵，{@code delete} 专用）：system 级限管理员（无登录态的内部
     * 调用按一期行为放行——GraphQL 入口在生产由 action-auth 管角色，此处为第二层）；user 级限
     * owner+管理员（无登录态拒绝——owner 无法判定）。
     *
     * <p>D1-03/D4-07 后 {@code saveCredential} 更新路径的越权拒绝已归一
     * {@link UnknownEntityException}（三态不可区分）；{@code delete} 因先行 canSee 归一，
     * 实际可达分支仅为"可见 system 级 + 非管理员"（显式 ADMIN_REQUIRED，A1-audit 认可的
     * 既有读路径口径）；user 级 OWNER_OR_ADMIN 分支为纵深防御保留（canSee 已拦截，正常不可达）。
     */
    private void assertWriteAllowed(NopCredential entity) {
        IUserContext userContext = IUserContext.get();
        String denial = CredentialOwnership.writeDenialReason(userContext, entity.getScope(), entity.getOwnerId());
        if (denial == null) {
            return;
        }
        if ("admin-required".equals(denial)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_ADMIN_REQUIRED)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, entity.getCredentialId())
                    .param(CredentialErrors.ARG_REQUIRED_ROLES,
                            CredentialOwnership.adminRolesAsString(CredentialOwnership.adminRoles()));
        }
        throw new NopException(CredentialErrors.ERR_CREDENTIAL_OWNER_OR_ADMIN)
                .param(CredentialErrors.ARG_CREDENTIAL_ID, entity.getCredentialId())
                .param(CredentialErrors.ARG_OWNER_ID, entity.getOwnerId());
    }

    /**
     * 归属不可变（设计 §5.3）：update 路径 scope/ownerId 缺省不传 = 保持不变；显式传入且与
     * 存量不符即拒（存量 NULL scope 视同 system，传入 system 视为等值——顺带把 NULL 规范化为
     * 显式 'system'，"新写入恒显式值"）。"转让"语义 = 删除旧凭证 + 新建（引用计数拦截保护消费方）。
     */
    private void applyImmutableOwnershipInput(NopCredential entity, String scope, String ownerId) {
        if (scope != null) {
            String storedScope = entity.getScope() == null ? CredentialOwnership.SCOPE_SYSTEM : entity.getScope();
            if (!scope.equals(storedScope)) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_OWNERSHIP_IMMUTABLE)
                        .param(CredentialErrors.ARG_CREDENTIAL_ID, entity.getCredentialId())
                        .param(CredentialErrors.ARG_SCOPE, scope)
                        .param(CredentialErrors.ARG_OWNER_ID, entity.getOwnerId());
            }
            entity.setScope(scope); // NULL → 显式 system 的等值规范化
        }
        if (ownerId != null && !ownerId.equals(entity.getOwnerId())) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_OWNERSHIP_IMMUTABLE)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, entity.getCredentialId())
                    .param(CredentialErrors.ARG_SCOPE, entity.getScope())
                    .param(CredentialErrors.ARG_OWNER_ID, ownerId);
        }
    }

    /**
     * 单条访问可见性预检（maskList/test 用）：不可见（含不存在）一律按 {@code ERR_CREDENTIAL_NOT_FOUND}
     * 归一——先于 provider 调用，避免 provider 的显式归属错误码泄露归属存在性（设计 §5.3）。
     */
    private void requireVisibleForSingleAccess(String credentialId) {
        IUserContext userContext = IUserContext.get();
        NopCredential entity = dao().getEntityById(credentialId);
        if (entity == null || !CredentialOwnership.canSee(userContext, entity.getScope(), entity.getOwnerId())) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }
    }

    // ==================== 自定义查询 action ====================

    /**
     * 批量返回凭证的脱敏视图（敏感字段→{@code ****}，非敏感字段截断）。
     * 用于 UI 展示，不暴露明文。
     *
     * <p>W11：BizModel 层先做行级可见性预检（不可见即按 NOT_FOUND 归一，再调 provider——
     * 否则 provider 的显式归属错误码会泄露归属存在性）；provider 层 mask 归属校验
     * （user 级 owner+admin）为纵深防御。
     */
    @Description("批量获取凭证脱敏视图")
    @BizQuery
    public List<MaskedCredential> maskList(@Name("ids") List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<MaskedCredential> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            requireVisibleForSingleAccess(id);
            result.add(credentialProvider.mask(id));
        }
        return result;
    }

    /**
     * 列出已注册的全部凭证类型及其字段 schema，供前端动态表单渲染。
     *
     * <p>W9：oauth2 类型裁剪掉引擎保留字段（表单只出人工字段；registry 已拒绝类型文件占用
     * 保留名，此处为防御性双保险）。返回浅拷贝，不改写 registry 缓存对象。
     */
    @Description("列出凭证类型及字段 schema")
    @BizQuery
    public List<CredentialType> typeList() {
        List<CredentialType> types = credentialTypeRegistry.listTypes();
        List<CredentialType> result = new ArrayList<>(types.size());
        for (CredentialType type : types) {
            if (type.isOauth2Type()) {
                CredentialType copy = new CredentialType();
                copy.setName(type.getName());
                copy.setVersion(type.getVersion());
                copy.setDisplayName(type.getDisplayName());
                copy.setAuthType(type.getAuthType());
                copy.setTestUrl(type.getTestUrl());
                copy.setTestAuth(type.getTestAuth());
                copy.setOauth2(type.getOauth2());
                copy.setFields(type.getFields().stream()
                        .filter(f -> !CredentialType.OAUTH_RESERVED_FIELD_NAMES.contains(f.getName()))
                        .collect(Collectors.toList()));
                result.add(copy);
            } else {
                result.add(type);
            }
        }
        return result;
    }

    // ==================== 测试连通性 ====================

    /**
     * 触发凭证连通性测试，更新实体的 {@code testResult} 和 {@code lastUsedAt}。
     *
     * <p>W11：BizModel 层先做行级可见性预检（不可见即按 NOT_FOUND 归一，防归属探测）；
     * provider 层 testCredential 归属校验（user 级 owner+admin）为纵深防御。
     */
    @Description("测试凭证连通性")
    @BizMutation
    public TestResult test(@Name("id") String credentialId) {
        requireVisibleForSingleAccess(credentialId);
        TestResult result = credentialProvider.testCredential(credentialId);

        // 额外更新 lastUsedAt（testCredential 只更新 testResult）
        IEntityDao<NopCredential> dao = dao();
        NopCredential entity = dao.getEntityById(credentialId);
        if (entity != null) {
            entity.setLastUsedAt(new Timestamp(System.currentTimeMillis()));
            dao.updateEntityDirectly(entity);
        }
        return result;
    }

    // ==================== 批量重新加密（密钥轮换） ====================

    /**
     * reencryptAll 分页页大小。由配置项 {@code nop.credential.reencrypt-page-size} 指定
     * （缺省 1000，保持一期单页语义），可注入缩小以便测试超批量场景。
     * 字段为 protected 以兼容 NopIoC 字段注入。
     */
    protected int reencryptPageSize = 1000;

    @InjectValue("@cfg:nop.credential.reencrypt-page-size|1000")
    public void setReencryptPageSize(int reencryptPageSize) {
        // D3-02（A1-audit successor，2026-08-17）：页大小下限 fail-closed（< 1 拒绝）——
        // 0/负值页大小使 keyset 翻页循环每页取空、游标永不推进（空页死循环）
        if (reencryptPageSize < 1) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_REENCRYPT_PAGE_SIZE_INVALID)
                    .param("reencryptPageSize", reencryptPageSize);
        }
        this.reencryptPageSize = reencryptPageSize;
    }

    /**
     * 批量重新加密所有未删除的凭证，使用 active key。
     *
     * <p><b>W10 分页完备性修复</b>：一期实现单页查询（limit=1000、无翻页循环），
     * 凭证量超出页大小时单次执行不保证全覆盖——KMS 迁移关窗所依赖的"全部密文
     * 已切换到新 key 集"完备性不成立。本实现改为确定性排序（orderBy credentialId）
     * + 游标（keyset）翻页循环直至取尽：无排序的 offset 分页会漏行/重行，恰是
     * 完备性缺陷的变形，故强制排序 + 游标而非裸 offset。
     *
     * <p>幂等：若凭证密文中的 keyId 已等于 active keyId，则跳过（避免不必要的重新加密）。
     * 逐条提交可重跑（单条失败抛错中止，重跑从断点语义继续——已处理条目幂等跳过）。
     * 失败 fail-closed（解密/加密异常抛出，不静默跳过）。
     *
     * @return 实际重新加密的凭证数量
     */
    @Description("批量重新加密所有凭证（密钥轮换）")
    @BizMutation
    public int reencryptAll() {
        // W11：全量敏感操作限管理员（进程内重加密不出明文，但触及全部密文；无登录态内部
        // 调用按一期行为放行——KMS 迁移等运维通道）
        IUserContext userContext = IUserContext.get();
        if (CredentialOwnership.hasLoginUser(userContext) && !CredentialOwnership.isAdmin(userContext)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_ADMIN_REQUIRED)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, "")
                    .param(CredentialErrors.ARG_REQUIRED_ROLES,
                            CredentialOwnership.adminRolesAsString(CredentialOwnership.adminRoles()));
        }

        IEntityDao<NopCredential> dao = dao();
        String activeKeyId = keyProvider.getActiveKeyId();

        int updated = 0;
        int nonCv1Skipped = 0; // D5-03：非 cv1 前缀行计数（关窗完备性信号）
        String cursor = null;
        List<NopCredential> page;
        do {
            QueryBean query = buildReencryptQuery();
            query.setCursor(cursor);
            page = dao.findPageByQuery(query);

            for (NopCredential entity : page) {
                String data = entity.getData();
                if (StringHelper.isEmpty(data) || !data.startsWith(CredentialCipher.CV1_MARKER)) {
                    nonCv1Skipped++; // D5-03：静默跳过改为可观测计数（不进 updated、不 fail——存量 legacy 行由关窗信号暴露）
                    continue;
                }

                String currentKeyId = extractKeyId(data);
                if (activeKeyId.equals(currentKeyId)) {
                    continue; // idempotent skip
                }

                try {
                    String json = credentialCipher.decrypt(data);
                    String newData = credentialCipher.encrypt(json);
                    entity.setData(newData);
                    dao.updateEntityDirectly(entity);
                    updated++;
                } catch (NopException e) {
                    throw new NopException(CredentialErrors.ERR_CREDENTIAL_REENCRYPT_FAILED, e)
                            .param(CredentialErrors.ARG_CREDENTIAL_ID, entity.getCredentialId());
                }
            }

            // 游标推进到本页最后一条（keyset：orderBy credentialId + cursor 语义上
            // 恒取"该 id 之后"的下一页，处理中不改 id 故不漏不重）
            if (!page.isEmpty()) {
                cursor = page.get(page.size() - 1).orm_idString();
            }
        } while (page.size() == reencryptPageSize);

        // D5-03（A1-audit successor，2026-08-17）：非 cv1 前缀行 WARN 汇总（含行数）——
        // KMS 迁移关窗的完备性信号（GraphQL 返回契约 int 保持不变）；包私有计数器供测试断言
        this.lastNonCv1SkippedCount = nonCv1Skipped;
        if (nonCv1Skipped > 0) {
            LOG.warn("reencryptAll skipped {} credential row(s) with non-cv1 or empty data prefix "
                    + "(legacy/manual rows remain unrotated — investigate before retiring old keys)",
                    nonCv1Skipped);
        }
        return updated;
    }

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(NopCredentialBizModel.class);

    /**
     * D5-03：最近一次 {@code reencryptAll} 跳过的非 cv1 前缀行数（包私有可断言暴露；
     * 与 WARN 汇总日志同源——测试不依赖日志 appender 也能断言计数信号）。
     */
    int lastNonCv1SkippedCount;

    /**
     * 构造 reencryptAll 的分页查询：强制 orderBy credentialId（确定性排序——keyset
     * 翻页完备性的前提）+ delFlag 过滤 + 可注入页大小。包私有以供测试断言排序确定性。
     */
    QueryBean buildReencryptQuery() {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("delFlag", 0));
        query.addOrderField("credentialId", false);
        query.setLimit(reencryptPageSize);
        return query;
    }

    /**
     * 从 {@code cv1:{keyId}:v1:...} 中提取 keyId。不做完整格式校验，仅做最小拆分。
     */
    private String extractKeyId(String cv1Text) {
        String rest = cv1Text.substring(CredentialCipher.CV1_MARKER.length());
        int colonIdx = rest.indexOf(':');
        if (colonIdx <= 0) {
            return null;
        }
        return rest.substring(0, colonIdx);
    }

    // ==================== 删除引用计数拦截 + W11 写分级 ====================

    /**
     * 覆盖标准 {@code delete}：删除前先做 W11 写分级与可见性归一，再做引用计数检查，活跃引用
     * 存在时 fail-closed（遵循 Plan Phase 2 + Rule #24 禁止静默跳过）。
     *
     * <p>W11：不可见目标（非管理员访问他人 user 级）归一"不存在"（UnknownEntityException，
     * 与 {@code get} 同口径）；可见目标的写分级 = system 级限管理员（无登录态内部调用按一期
     * 行为放行）、user 级限 owner+管理员。
     *
     * <p>委托给 {@link CrudBizModel#doDelete}，通过 {@code prepareDelete} 回调插入引用检查与
     * 业务级禁用。ORM 的 {@code useLogicalDelete} 会自动设置 {@code delFlag=true}，
     * 本回调额外设置 {@code status=disabled}（业务层关闭）。
     *
     * @throws NopException {@link CredentialErrors#ERR_CREDENTIAL_HAS_ACTIVE_USAGE} 当存在活跃引用时
     */
    @Description("@i18n:biz.delete|根据主键删除指定对象")
    @BizMutation
    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopCredential entity = dao().getEntityById(id);
        if (entity == null) {
            return true; // 与基类 doDelete 的幂等语义一致
        }
        IUserContext userContext = IUserContext.get();
        if (!CredentialOwnership.canSee(userContext, entity.getScope(), entity.getOwnerId())) {
            // 单条越权归一"不存在"（防 credentialId 枚举探测归属）
            throw new UnknownEntityException(getEntityName(), id);
        }
        assertWriteAllowed(entity);
        return super.doDelete(id, null, this::prepareDeleteWithUsageCheck, context);
    }

    /**
     * 删除前置回调：(1) 查询 {@link NopCredentialUsage} 按 {@code credentialId} 的活跃引用数，
     * 若 &gt; 0 抛出 {@link CredentialErrors#ERR_CREDENTIAL_HAS_ACTIVE_USAGE}（fail-closed）；
     * (2) W11 Part B 授权行物理级联清理——凭证删除时其 {@link NopCredentialAuth} 授权行
     * 一并物理删除（子 BizModel 的 delete 动作已禁用——revoke 为唯一删除通道，故不走
     * biz 级联 tag，改在本回调内经 dao 显式清理，语义与 to-many cascadeDelete 声明一致；
     * 在 usage 检查之后执行——删除被拦截时授权行与凭证同存属正常语义）；
     * (3) 业务级禁用——置 {@code status=disabled}（ORM 软删除 {@code delFlag} 由 dao 自动处理）。
     */
    private void prepareDeleteWithUsageCheck(NopCredential entity, IServiceContext context) {
        IEntityDao<NopCredentialUsage> usageDao = daoFor(NopCredentialUsage.class);
        QueryBean usageQuery = new QueryBean();
        usageQuery.addFilter(FilterBeans.eq(NopCredentialUsage.PROP_NAME_credentialId,
                entity.getCredentialId()));
        long count = usageDao.countByQuery(usageQuery);
        if (count > 0) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_HAS_ACTIVE_USAGE)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, entity.getCredentialId())
                    .param(CredentialErrors.ARG_USAGE_COUNT, count);
        }
        // W11 Part B：授权行随凭证删除物理级联清理（在 usage 拦截之后）
        IEntityDao<NopCredentialAuth> authDao = daoFor(NopCredentialAuth.class);
        QueryBean authQuery = new QueryBean();
        authQuery.addFilter(FilterBeans.eq(NopCredentialAuth.PROP_NAME_credentialId,
                entity.getCredentialId()));
        for (NopCredentialAuth auth : authDao.findAllByQuery(authQuery)) {
            authDao.deleteEntityDirectly(auth);
        }
        // 业务级禁用（ORM useLogicalDelete 会同时设置 delFlag=true）
        entity.setStatus("disabled");
    }

    // ==================== W11 继承动作面收口（六个旁路，设计 §5.3） ====================

    /**
     * 禁用标准 {@code update}（与标准 {@code save} 同口径）：绕过加密（data 直写）、归属不可变
     * 与写分级校验的旁路，更新必须走 {@link #saveCredential}。
     */
    @Description("禁用标准 update，请使用 saveCredential")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredential update(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("use saveCredential action instead: standard update bypasses encryption/ownership checks");
    }

    /**
     * 禁用标准 {@code batchDelete}：绕过逐条写分级与归属不可变语义；删除必须逐条走
     * {@link #delete}（引用计数拦截 + 写分级）。
     */
    @Description("禁用标准 batchDelete，请逐条使用 delete")
    @BizMutation
    @Override
    public Set<String> batchDelete(@Name("ids") Set<String> ids, IServiceContext context) {
        throw new UnsupportedOperationException("standard batchDelete is disabled for credentials: use delete action per id");
    }

    /**
     * 禁用 {@code updateByQuery}：基类实现 {@code prepareQuery} 传 null，绕过读类结构性过滤
     * 批量改元数据（归属列也在元数据面）。
     */
    @Description("禁用 updateByQuery（绕过归属过滤的旁路）")
    @BizMutation
    @Override
    public int updateByQuery(@Name("query") QueryBean query, @Name("data") Map<String, Object> data,
                             IServiceContext context) {
        throw new UnsupportedOperationException("updateByQuery is disabled for credentials: it bypasses the ownership read filter");
    }

    /**
     * 禁用 {@code deleteByQuery}：基类实现经 {@code doDeleteByQuery → doDeleteMulti → doDelete}
     * 调用 {@code invokeDefaultPrepareDelete}，不经过本类覆盖的 {@code delete}/引用计数拦截，
     * 破坏一期"活跃引用存在时拒绝删除"契约，必须禁用。
     */
    @Description("禁用 deleteByQuery（绕过引用计数拦截，破坏一期契约）")
    @BizMutation
    @Override
    public int deleteByQuery(@Name("query") QueryBean query, IServiceContext context) {
        throw new UnsupportedOperationException("deleteByQuery is disabled for credentials: it bypasses the usage-reference-count interception");
    }

    /**
     * 禁用 {@code copyForNew}：读源实体克隆保存在 {@code saveCredential} 之外复制凭证行
     * （含密文 data），绕过加密入口与归属创建规则。
     */
    @Description("禁用 copyForNew（在 saveCredential 之外复制密文行）")
    @BizMutation
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredential copyForNew(@Name("data") Map<String, Object> data, IServiceContext context) {
        throw new UnsupportedOperationException("copyForNew is disabled for credentials: it duplicates the ciphertext row outside saveCredential");
    }

    /**
     * {@code batchGet} 改走行级可见性过滤语义（W11 收口裁定：过滤而非禁用——UI 批量取数合法
     * 场景保留）：不可见行从结果中剔除（与 {@code get} 的"归一不存在"同口径的批量形式）。
     * D1-01（A1-audit successor，2026-08-17）：返回前逐行驱逐 + {@code setData(null)}
     * （与 {@code get}/{@code findPage} 同口径，闭掉"可见性过滤已做但密文仍在返回实体上"
     * 的防御缺口）。
     * （{@code batchUpdate}/{@code batchModify}/{@code saveOrUpdate} 内部委托已禁用的
     * {@code update}/{@code save}，禁用后自动失效。）
     */
    @Description("@i18n:biz.batchGet|根据主键批量获取对象")
    @BizQuery
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public List<NopCredential> batchGet(@Name("ids") Collection<String> ids,
                                        @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                                        IServiceContext context) {
        List<NopCredential> list = super.batchGet(ids, ignoreUnknown, context);
        if (list.isEmpty()) {
            return list;
        }
        // D1-01：先逐行驱逐 + 清密文（覆盖全部返回行，再做可见性过滤）
        for (NopCredential entity : list) {
            orm().requireSession().evict(entity);
            entity.setData(null);
        }
        IUserContext userContext = IUserContext.get();
        if (!CredentialOwnership.hasLoginUser(userContext) || CredentialOwnership.isAdmin(userContext)) {
            return list;
        }
        return list.stream()
                .filter(entity -> CredentialOwnership.canSee(userContext, entity.getScope(), entity.getOwnerId()))
                .collect(Collectors.toList());
    }
}
