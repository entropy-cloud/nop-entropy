/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.registry;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.credential.api.registry.CredentialType;
import io.nop.credential.api.registry.ICredentialTypeRegistry;
import io.nop.credential.crypto.CredentialErrors;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
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
 */
public class DefaultCredentialTypeRegistry implements ICredentialTypeRegistry {

    public static final String TYPES_DIR = "/nop/credential/types";
    public static final String FILE_SUFFIX = ".credential-type.xml";

    private Map<String, CredentialType> typeMap = Collections.emptyMap();

    @PostConstruct
    public void init() {
        Map<String, CredentialType> map = new LinkedHashMap<>();

        List<IResource> resources = VirtualFileSystem.instance().findAll(TYPES_DIR, "*" + FILE_SUFFIX);
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

        return type;
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
