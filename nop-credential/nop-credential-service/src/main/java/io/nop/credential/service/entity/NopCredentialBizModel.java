/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
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
import io.nop.credential.dao.entity.NopCredentialUsage;
import io.nop.credential.service.CredentialProviderImpl;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.biz.BizConstants.BIZ_OBJ_NAME_THIS_OBJ;

/**
 * {@code NopCredential} 的管理 CRUD BizModel。
 *
 * <p><b>结构性明文边界</b>：xmeta 中 {@code data} 字段为 {@code published="false"}，因此标准 GraphQL
 * schema 不暴露密文。本类在此基础上做三重防御：
 * <ol>
 *   <li>继承的标准 {@code save} 被覆盖为抛出异常，强制走自定义 {@link #saveCredential} 路径（明文输入唯一入口）</li>
 *   <li>{@link #get} / {@link #findPage} 在返回前强制 {@code entity.setData(null)}（即使 xmeta 边界被绕过也无密文）</li>
 *   <li>{@link #saveCredential} 内部加密后持久化，返回的实体 {@code data} 已置 null</li>
 * </ol>
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
     * <p>返回的实体 {@code data} 已置 null（不向调用方暴露密文）。
     *
     * @param typeName 凭证类型名（必须已注册，否则 fail-closed）
     * @param name     凭证显示名
     * @param fields   明文字段 map（必填、非空）
     * @param id       可选；提供则更新已存在凭证，不提供则新建
     */
    @Description("保存凭证（明文输入路径，内部加密后持久化）")
    @BizMutation
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredential saveCredential(
            @Name("typeName") String typeName,
            @Name("name") String name,
            @Name("fields") Map<String, Object> fields,
            @Optional @Name("id") String id,
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
            entity = dao.newEntity();
            entity.setCredentialId(StringHelper.generateUUID());
            entity.setDelFlag((byte) 0);
            entity.setVersion(1);
            entity.setCreateTime(new Timestamp(System.currentTimeMillis()));

            // 新建：保留字段已被拒绝出现，直接整包加密（一期路径不变）
            entity.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
        } else {
            entity = dao.getEntityById(id);
            if (entity == null) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, id);
            }

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

    // ==================== 明文边界：get / findPage 强制清空 data ====================

    @Description("@i18n:biz.get|根据id获取单条数据")
    @BizQuery
    @Override
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopCredential get(@Name("id") String id,
                             @Optional @Name("ignoreUnknown") boolean ignoreUnknown,
                             IServiceContext context) {
        NopCredential entity = super.get(id, ignoreUnknown, context);
        if (entity != null) {
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

    // ==================== 自定义查询 action ====================

    /**
     * 批量返回凭证的脱敏视图（敏感字段→{@code ****}，非敏感字段截断）。
     * 用于 UI 展示，不暴露明文。
     */
    @Description("批量获取凭证脱敏视图")
    @BizQuery
    public List<MaskedCredential> maskList(@Name("ids") List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<MaskedCredential> result = new ArrayList<>(ids.size());
        for (String id : ids) {
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
     */
    @Description("测试凭证连通性")
    @BizMutation
    public TestResult test(@Name("id") String credentialId) {
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
        IEntityDao<NopCredential> dao = dao();
        String activeKeyId = keyProvider.getActiveKeyId();

        int updated = 0;
        String cursor = null;
        List<NopCredential> page;
        do {
            QueryBean query = buildReencryptQuery();
            query.setCursor(cursor);
            page = dao.findPageByQuery(query);

            for (NopCredential entity : page) {
                String data = entity.getData();
                if (StringHelper.isEmpty(data) || !data.startsWith(CredentialCipher.CV1_MARKER)) {
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
        return updated;
    }

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

    // ==================== 删除引用计数拦截 ====================

    /**
     * 覆盖标准 {@code delete}：在 ORM 删除前先做引用计数检查，活跃引用存在时 fail-closed
     * （遵循 Plan Phase 2 + Rule #24 禁止静默跳过）。
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
        return super.doDelete(id, null, this::prepareDeleteWithUsageCheck, context);
    }

    /**
     * 删除前置回调：(1) 查询 {@link NopCredentialUsage} 按 {@code credentialId} 的活跃引用数，
     * 若 &gt; 0 抛出 {@link CredentialErrors#ERR_CREDENTIAL_HAS_ACTIVE_USAGE}（fail-closed）；
     * (2) 业务级禁用——置 {@code status=disabled}（ORM 软删除 {@code delFlag} 由 dao 自动处理）。
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
        // 业务级禁用（ORM useLogicalDelete 会同时设置 delFlag=true）
        entity.setStatus("disabled");
    }
}
