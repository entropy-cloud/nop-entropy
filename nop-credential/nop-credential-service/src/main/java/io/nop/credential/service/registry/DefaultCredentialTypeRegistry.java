/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.registry;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.credential.api.registry.CredentialType;
import io.nop.credential.api.registry.ICredentialTypeRegistry;
import io.nop.credential.crypto.CredentialErrors;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ICredentialTypeRegistry} 的默认实现。
 *
 * <p>初始化时枚举 {@code _vfs/nop/credential/types/} 下所有 {@code *.credential-type.xml} 实例文件，
 * 经 {@link ResourceComponentManager} 加载（xdef 校验），映射为手写的 {@link CredentialType} DTO，
 * 按 {@code name} 属性缓存到 {@link Map}。
 *
 * <p>所有失败路径（文件加载异常、未知类型名查询）均抛出 {@link NopException}，不静默跳过（Rule #24）。
 * 未知类型查询 fail-closed，永不返回 null。
 *
 * <p>W9 二期加载校验（全部 fail-closed，无静默跳过）：
 * <ul>
 *   <li>保留字段名（accessToken/refreshToken/expiresAt/tokenType/scope）被类型文件 fields 占用 → 拒绝</li>
 *   <li>authType 取值域外（none|apiKey|basic|oauth2）→ 拒绝（xdef 内联枚举之外的双保险）</li>
 *   <li>authType=oauth2 但缺 authorizationEndpoint/tokenEndpoint → 拒绝（缺端点元数据的 oauth2 类型不可用，加载期暴露）</li>
 *   <li>非 oauth2 类型声明 {@code <oauth2>} 元数据 → 拒绝（防配置错位被静默忽略）</li>
 * </ul>
 */
public class DefaultCredentialTypeRegistry implements ICredentialTypeRegistry {

    public static final String TYPES_DIR = "/nop/credential/types";
    public static final String FILE_SUFFIX = ".credential-type.xml";

    private static final List<String> VALID_AUTH_TYPES = Collections.unmodifiableList(
            Arrays.asList(CredentialType.AUTH_TYPE_NONE, CredentialType.AUTH_TYPE_API_KEY,
                    CredentialType.AUTH_TYPE_BASIC, CredentialType.AUTH_TYPE_OAUTH2));

    private String typesDir = TYPES_DIR;

    private Map<String, CredentialType> typeMap = Collections.emptyMap();

    /**
     * 允许测试注入自定义类型目录（生产缺省 {@link #TYPES_DIR}）。
     */
    public void setTypesDir(String typesDir) {
        this.typesDir = typesDir;
    }

    @PostConstruct
    public void init() {
        Map<String, CredentialType> map = new LinkedHashMap<>();

        List<IResource> resources = VirtualFileSystem.instance().findAll(typesDir, "*" + FILE_SUFFIX);
        for (IResource resource : resources) {
            CredentialType type = loadType(resource);
            if (type.getName() == null) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_TYPE_LOAD_FAILED)
                        .param(CredentialErrors.ARG_TYPE_NAME, resource.getPath());
            }
            if (map.put(type.getName(), type) != null) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_TYPE_LOAD_FAILED)
                        .param(CredentialErrors.ARG_TYPE_NAME, type.getName());
            }
        }

        this.typeMap = Collections.unmodifiableMap(map);
    }

    @SuppressWarnings("unchecked")
    private CredentialType loadType(IResource resource) {
        Object model;
        try {
            model = ResourceComponentManager.instance().loadComponentModel(resource.getPath());
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_TYPE_LOAD_FAILED, e)
                    .param(CredentialErrors.ARG_TYPE_NAME, resource.getPath());
        }

        DynamicObject obj = (DynamicObject) model;

        CredentialType type = new CredentialType();
        type.setName((String) obj.prop_get("name"));
        type.setVersion((String) obj.prop_get("version"));
        type.setDisplayName((String) obj.prop_get("displayName"));
        type.setAuthType((String) obj.prop_get("authType"));
        type.setTestUrl((String) obj.prop_get("testUrl"));
        type.setTestAuth((String) obj.prop_get("testAuth"));

        Object fieldsObj = obj.prop_get("fields");
        if (fieldsObj instanceof List) {
            List<CredentialType.CredentialField> fields = new ArrayList<>();
            for (Object item : (List<Object>) fieldsObj) {
                DynamicObject fieldObj = (DynamicObject) item;
                CredentialType.CredentialField field = new CredentialType.CredentialField();
                field.setName((String) fieldObj.prop_get("name"));
                field.setLabel((String) fieldObj.prop_get("label"));
                field.setType((String) fieldObj.prop_get("type"));
                Object sensitive = fieldObj.prop_get("sensitive");
                field.setSensitive(sensitive != null && (boolean) sensitive);
                field.setDefaultValue((String) fieldObj.prop_get("defaultValue"));
                Object required = fieldObj.prop_get("required");
                field.setRequired(required != null && (boolean) required);
                fields.add(field);
            }
            type.setFields(fields);
        }

        Object oauthObj = obj.prop_get("oauth2");
        if (oauthObj instanceof DynamicObject) {
            DynamicObject oauthNode = (DynamicObject) oauthObj;
            CredentialType.OAuth2Metadata metadata = new CredentialType.OAuth2Metadata();
            metadata.setAuthorizationEndpoint((String) oauthNode.prop_get("authorizationEndpoint"));
            metadata.setTokenEndpoint((String) oauthNode.prop_get("tokenEndpoint"));
            metadata.setScopes((String) oauthNode.prop_get("scopes"));
            Object refreshWindow = oauthNode.prop_get("refreshWindowSeconds");
            if (refreshWindow instanceof Number) {
                metadata.setRefreshWindowSeconds(((Number) refreshWindow).intValue());
            }
            type.setOauth2(metadata);
        }

        validate(type, resource);
        return type;
    }

    /**
     * W9 加载校验（全部 fail-closed）。任何违规抛 {@link NopException}，不静默跳过。
     */
    private void validate(CredentialType type, IResource resource) {
        String typeName = type.getName() != null ? type.getName() : resource.getPath();

        // 1. authType 取值域
        if (type.getAuthType() == null || !VALID_AUTH_TYPES.contains(type.getAuthType())) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_TYPE_INVALID_AUTH_TYPE)
                    .param(CredentialErrors.ARG_TYPE_NAME, typeName)
                    .param(CredentialErrors.ARG_AUTH_TYPE, type.getAuthType());
        }

        // 2. 保留字段名占用拒绝
        List<String> reservedUsed = new ArrayList<>();
        for (CredentialType.CredentialField field : type.getFields()) {
            if (CredentialType.OAUTH_RESERVED_FIELD_NAMES.contains(field.getName())) {
                reservedUsed.add(field.getName());
            }
        }
        if (!reservedUsed.isEmpty()) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_TYPE_RESERVED_FIELD)
                    .param(CredentialErrors.ARG_TYPE_NAME, typeName)
                    .param(CredentialErrors.ARG_FIELD_NAMES, reservedUsed);
        }

        // 3/4. oauth2 元数据完备性与错位拒绝
        if (type.isOauth2Type()) {
            CredentialType.OAuth2Metadata metadata = type.getOauth2();
            if (metadata == null || StringHelper.isEmpty(metadata.getAuthorizationEndpoint())
                    || StringHelper.isEmpty(metadata.getTokenEndpoint())) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_TYPE_OAUTH_METADATA_MISSING)
                        .param(CredentialErrors.ARG_TYPE_NAME, typeName);
            }
        } else if (type.getOauth2() != null) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_TYPE_OAUTH_METADATA_NOT_ALLOWED)
                    .param(CredentialErrors.ARG_TYPE_NAME, typeName)
                    .param(CredentialErrors.ARG_AUTH_TYPE, type.getAuthType());
        }
    }

    @Override
    public CredentialType getType(String typeName) {
        CredentialType type = typeMap.get(typeName);
        if (type == null) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_UNKNOWN_TYPE)
                    .param(CredentialErrors.ARG_TYPE_NAME, typeName)
                    .param(CredentialErrors.ARG_AVAILABLE_TYPE_NAMES, typeMap.keySet());
        }
        return type;
    }

    @Override
    public List<CredentialType> listTypes() {
        return new ArrayList<>(typeMap.values());
    }
}
