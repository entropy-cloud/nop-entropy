/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.credential.api.CredentialData;
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
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
        credentialTypeRegistry.getType(typeName); // throws ERR_CREDENTIAL_UNKNOWN_TYPE

        if (StringHelper.isEmpty(name)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_NAME_REQUIRED)
                    .param(CredentialErrors.ARG_TYPE_NAME, typeName);
        }

        if (fields == null || fields.isEmpty()) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_FIELDS_REQUIRED)
                    .param(CredentialErrors.ARG_TYPE_NAME, typeName);
        }

        // 序列化为 JSON，加密为 cv1: 密文（使用 active key）
        String json = JsonTool.stringify(fields);
        String ciphertext = credentialCipher.encrypt(json);

        IEntityDao<NopCredential> dao = dao();
        NopCredential entity;
        boolean isNew = StringHelper.isEmpty(id);
        if (isNew) {
            entity = dao.newEntity();
            entity.setCredentialId(StringHelper.generateUUID());
            entity.setDelFlag((byte) 0);
            entity.setVersion(1);
            entity.setCreateTime(new Timestamp(System.currentTimeMillis()));
        } else {
            entity = dao.getEntityById(id);
            if (entity == null) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)
                        .param(CredentialErrors.ARG_CREDENTIAL_ID, id);
            }
        }

        entity.setTypeName(typeName);
        entity.setName(name);
        entity.setData(ciphertext);
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
     */
    @Description("列出凭证类型及字段 schema")
    @BizQuery
    public List<CredentialType> typeList() {
        return credentialTypeRegistry.listTypes();
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
     * 批量重新加密所有未删除的凭证，使用 active key。
     *
     * <p>幂等：若凭证密文中的 keyId 已等于 active keyId，则跳过（避免不必要的重新加密）。
     * 失败 fail-closed（解密/加密异常抛出，不静默跳过）。
     *
     * @return 实际重新加密的凭证数量
     */
    @Description("批量重新加密所有凭证（密钥轮换）")
    @BizMutation
    public int reencryptAll() {
        IEntityDao<NopCredential> dao = dao();
        String activeKeyId = keyProvider.getActiveKeyId();

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("delFlag", 0));
        query.setLimit(1000);
        List<NopCredential> all = dao.findPageByQuery(query);

        int updated = 0;
        for (NopCredential entity : all) {
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
        return updated;
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
