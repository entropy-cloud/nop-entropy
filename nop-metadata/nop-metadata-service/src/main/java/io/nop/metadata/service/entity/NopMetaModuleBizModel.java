/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.metadata.biz.INopMetaModuleBiz;
import io.nop.metadata.dao.entity.NopMetaDict;
import io.nop.metadata.dao.entity.NopMetaDictItem;
import io.nop.metadata.dao.entity.NopMetaDomain;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaEntityIndex;
import io.nop.metadata.dao.entity.NopMetaEntityRelation;
import io.nop.metadata.dao.entity.NopMetaEntityUniqueKey;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.model.OrmModelImporter;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmIndexModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmUniqueKeyModel;
import io.nop.orm.model.loader.OrmModelLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@BizModel("NopMetaModule")
public class NopMetaModuleBizModel extends CrudBizModel<NopMetaModule> implements INopMetaModuleBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaModuleBizModel.class);

    static final ErrorCode ERR_RESOURCE_NOT_FOUND =
            ErrorCode.define("metadata.orm-resource-not-found", "ORM资源不存在", "path");
    static final ErrorCode ERR_RESOURCE_READ_FAILED =
            ErrorCode.define("metadata.orm-resource-read-failed", "ORM资源读取失败", "path");

    public NopMetaModuleBizModel() {
        setEntityName(NopMetaModule.class.getName());
    }

    @BizMutation
    public NopMetaModule importOrmModel(@Name("path") String path, IServiceContext context) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (resource == null || !resource.exists())
            throw new NopException(ERR_RESOURCE_NOT_FOUND).param("path", path);

        OrmModel ormModel = new OrmModelLoader().loadFromResource(resource, true);
        String sourceContent = readText(resource);

        OrmModelImporter importer = new OrmModelImporter();

        NopMetaModule module = importer.buildModule(ormModel);
        checkDataAuth(BizConstants.METHOD_SAVE, module, context);
        orm().save(module);
        String moduleId = module.getMetaModuleId();

        NopMetaOrmModel ormModelEntity = importer.buildOrmModel(ormModel, sourceContent);
        ormModelEntity.setMetaModuleId(moduleId);
        orm().save(ormModelEntity);
        String ormModelId = ormModelEntity.getOrmModelId();

        for (IEntityModel em : ormModel.getEntityModels()) {
            NopMetaEntity entity = importer.buildEntity(em);
            entity.setOrmModelId(ormModelId);
            orm().save(entity);
            String entityId = entity.getMetaEntityId();

            for (IColumnModel col : em.getColumns()) {
                NopMetaEntityField field = importer.buildField(col);
                field.setMetaEntityId(entityId);
                orm().save(field);
            }

            for (NopMetaEntityRelation rel : importer.buildRelations(em)) {
                rel.setMetaEntityId(entityId);
                orm().save(rel);
            }

            NopMetaTable table = importer.buildEntityTable(em);
            table.setMetaModuleId(moduleId);
            table.setBaseEntityId(entityId);
            orm().save(table);

            if (em instanceof OrmEntityModel) {
                OrmEntityModel oem = (OrmEntityModel) em;
                for (OrmUniqueKeyModel ukModel : oem.getUniqueKeys()) {
                    NopMetaEntityUniqueKey uk = importer.buildUniqueKey(ukModel);
                    uk.setMetaEntityId(entityId);
                    orm().save(uk);
                }
                for (OrmIndexModel idxModel : oem.getIndexes()) {
                    NopMetaEntityIndex idx = importer.buildIndex(idxModel);
                    idx.setMetaEntityId(entityId);
                    orm().save(idx);
                }
            }
        }

        for (OrmDomainModel domain : ormModel.getDomains()) {
            NopMetaDomain metaDomain = importer.buildDomain(domain);
            metaDomain.setOrmModelId(ormModelId);
            orm().save(metaDomain);
        }

        for (DictBean dict : ormModel.getDicts()) {
            NopMetaDict metaDict = importer.buildDict(dict);
            metaDict.setOrmModelId(ormModelId);
            orm().save(metaDict);
            String dictId = metaDict.getMetaDictId();

            for (NopMetaDictItem item : importer.buildDictItems(dict)) {
                item.setMetaDictId(dictId);
                orm().save(item);
            }
        }

        orm().flushSession();
        return module;
    }

    @BizMutation
    public List<Map<String, Object>> importOrmModels(@Name("paths") List<String> paths, IServiceContext context) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (paths == null)
            return results;

        for (String path : paths) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            try {
                NopMetaModule module = importOrmModel(path, context);
                result.put("metaModuleId", module.getMetaModuleId());
                result.put("moduleName", module.getModuleName());
                result.put("success", true);
            } catch (Exception e) {
                LOG.error("importOrmModels failed for path: {}", path, e);
                result.put("success", false);
                result.put("error", toErrorMessage(e));
                // 单个导入失败后清理 session，避免未刷新的脏实体或约束违例状态
                // 污染 session 导致后续导入级联失败
                orm().clearSession();
            }
            results.add(result);
        }
        return results;
    }

    private static String readText(IResource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopException(ERR_RESOURCE_READ_FAILED).param("path", resource.getPath()).cause(e);
        }
    }

    private static String toErrorMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getName();
    }
}
