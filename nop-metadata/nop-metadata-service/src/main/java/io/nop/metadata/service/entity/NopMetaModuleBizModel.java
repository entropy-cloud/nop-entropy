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
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceDslNodeLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.metadata.biz.INopMetaModuleBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
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
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmUniqueKeyModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.DslNodeLoader;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xmeta.SchemaLoader;

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
    static final ErrorCode ERR_MODULE_NOT_FOUND =
            ErrorCode.define("metadata.module-not-found", "Module not found: {metaModuleId}", "metaModuleId");
    static final ErrorCode ERR_MODULE_NOT_DRAFTING =
            ErrorCode.define("metadata.module-not-drafting", "Module is not in drafting status: {status}", "status");

    public NopMetaModuleBizModel() {
        setEntityName(NopMetaModule.class.getName());
    }

    @BizMutation
    public NopMetaModule importOrmModel(@Name("path") String path, IServiceContext context) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (resource == null || !resource.exists())
            throw new NopException(ERR_RESOURCE_NOT_FOUND).param("path", path);

        // full 定义：x:extends 完全展开后的完整模型
        OrmModel fullModel = new OrmModelLoader().loadFromResource(resource, true);
        String sourceContent = readText(resource);

        OrmModelImporter importer = new OrmModelImporter();

        long moduleVersion = computeNextModuleVersion(fullModel);
        NopMetaModule module = importer.buildModule(fullModel, moduleVersion);

        // baseModuleId 填充：从 x:extends 推导 base moduleId，查找已导入的 base 模块
        module.setBaseModuleId(resolveBaseModuleId(sourceContent, resource));

        checkDataAuth(BizConstants.METHOD_SAVE, module, context);
        orm().save(module);
        String moduleId = module.getMetaModuleId();

        // delta 定义：未展开 x:extends 的原始声明（无 x:extends 时与 full 相同）
        OrmModel deltaModel = parseDeltaModel(resource, fullModel, sourceContent);

        // isDelta 双重存储：delta（isDelta=true）+ full（isDelta=false），共用同一 metaModuleId
        persistModelGraph(importer, deltaModel, sourceContent, moduleId, true);
        persistModelGraph(importer, fullModel, sourceContent, moduleId, false);

        orm().flushSession();
        return module;
    }

    /**
     * 解析 delta 定义（未展开 x:extends）。无 x:extends 时返回 fullModel 本身。
     * 有 x:extends 时使用 {@link DslNodeLoader#loadDslNodeFromResource} 的 filtered 阶段
     * （x:include 已解析但 x:extends 未合并）获取原始 delta 节点，再通过 {@link DslModelParser} 解析为 OrmModel。
     */
    private OrmModel parseDeltaModel(IResource resource, OrmModel fullModel, String sourceContent) {
        if (!hasExtends(sourceContent))
            return fullModel;

        try {
            XNode deltaNode = DslNodeLoader.INSTANCE.loadDslNodeFromResource(resource,
                    OrmModelConstants.XDSL_SCHEMA_ORM, IResourceDslNodeLoader.ResolvePhase.filtered);
            IXDefinition xdef = SchemaLoader.loadXDefinition(OrmModelConstants.XDSL_SCHEMA_ORM);
            Object parsed = new DslModelParser(OrmModelConstants.XDSL_SCHEMA_ORM).disableInit(true)
                    .parseWithXDef(xdef, deltaNode);
            if (parsed instanceof OrmModel)
                return (OrmModel) parsed;
        } catch (Exception e) {
            LOG.warn("parseDeltaModel failed, falling back to full model as delta: {}", e.toString());
        }
        // 降级：delta 解析失败时，delta=full（内容相同）
        return fullModel;
    }

    private static boolean hasExtends(String sourceContent) {
        return sourceContent != null && sourceContent.contains("x:extends");
    }

    /**
     * 从 x:extends 属性推导 base 模块的 moduleId，查找已导入的 base MetaModule。
     * 若 orm.xml 无 x:extends 或 base 模块尚未导入，返回 null（不阻塞导入）。
     */
    private String resolveBaseModuleId(String sourceContent, IResource resource) {
        try {
            XNode node = XNodeParser.instance().parseFromText(
                    io.nop.api.core.util.SourceLocation.fromPath(resource.getPath()), sourceContent);
            XDslKeys keys = XDslKeys.of(node);
            String extendsPath = node.attrText(keys.EXTENDS);
            if (extendsPath == null || extendsPath.isEmpty())
                return null;

            // 解析 base orm.xml 获取其 appId/moduleId（x:extends 路径相对于当前资源目录）
            String resourcePath = resource.getPath();
            String dir = resourcePath.substring(0, resourcePath.lastIndexOf('/') + 1);
            String basePath = dir + extendsPath;
            IResource baseResource = VirtualFileSystem.instance().getResource(basePath);
            if (baseResource == null || !baseResource.exists())
                return null;

            OrmModel baseOrmModel = new OrmModelLoader().loadFromResource(baseResource, true);
            Object baseAppNameObj = baseOrmModel.prop_get(OrmModelConstants.EXT_APP_NAME);
            String baseAppName = baseAppNameObj != null ? String.valueOf(baseAppNameObj) : null;
            if (baseAppName == null)
                return null;
            String baseModuleId = baseAppName.replace('-', '/');

            // 查找已导入的 base 模块（取最新版本）
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, baseModuleId));
            query.addOrderField(NopMetaModule.PROP_NAME_moduleVersion, true);
            NopMetaModule baseModule = dao().findFirstByQuery(query);
            return baseModule != null ? baseModule.getMetaModuleId() : null;
        } catch (Exception e) {
            LOG.warn("resolveBaseModuleId failed, baseModuleId set to null: {}", e.toString());
            return null;
        }
    }

    /**
     * 持久化一组模型记录：NopMetaOrmModel + 其下所有子实体（Entity/Field/Relation/UK/Index/Domain/Dict/Table）。
     * 由 isDelta 参数控制所有子实体的 isDelta 标记。
     */
    private void persistModelGraph(OrmModelImporter importer, OrmModel ormModel, String sourceContent,
                                   String moduleId, boolean isDelta) {
        NopMetaOrmModel ormModelEntity = importer.buildOrmModel(ormModel, sourceContent, isDelta);
        ormModelEntity.setMetaModuleId(moduleId);
        orm().save(ormModelEntity);
        String ormModelId = ormModelEntity.getOrmModelId();

        for (IEntityModel em : ormModel.getEntityModels()) {
            NopMetaEntity entity = importer.buildEntity(em, isDelta);
            entity.setOrmModelId(ormModelId);
            orm().save(entity);
            String entityId = entity.getMetaEntityId();

            for (IColumnModel col : em.getColumns()) {
                NopMetaEntityField field = importer.buildField(col, isDelta);
                field.setMetaEntityId(entityId);
                orm().save(field);
            }

            for (NopMetaEntityRelation rel : importer.buildRelations(em, isDelta)) {
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
                    NopMetaEntityUniqueKey uk = importer.buildUniqueKey(ukModel, isDelta);
                    uk.setMetaEntityId(entityId);
                    orm().save(uk);
                }
                for (OrmIndexModel idxModel : oem.getIndexes()) {
                    NopMetaEntityIndex idx = importer.buildIndex(idxModel, isDelta);
                    idx.setMetaEntityId(entityId);
                    orm().save(idx);
                }
            }
        }

        for (OrmDomainModel domain : ormModel.getDomains()) {
            NopMetaDomain metaDomain = importer.buildDomain(domain, isDelta);
            metaDomain.setOrmModelId(ormModelId);
            orm().save(metaDomain);
        }

        for (DictBean dict : ormModel.getDicts()) {
            NopMetaDict metaDict = importer.buildDict(dict, isDelta);
            metaDict.setOrmModelId(ormModelId);
            orm().save(metaDict);
            String dictId = metaDict.getMetaDictId();

            for (NopMetaDictItem item : importer.buildDictItems(dict)) {
                item.setMetaDictId(dictId);
                orm().save(item);
            }
        }
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

    @BizMutation
    public NopMetaModule releaseModule(@Name("metaModuleId") String metaModuleId, IServiceContext context) {
        NopMetaModule module = dao().getEntityById(metaModuleId);
        if (module == null)
            throw new NopException(ERR_MODULE_NOT_FOUND).param("metaModuleId", metaModuleId);

        String status = module.getStatus();
        if (!_NopMetadataCoreConstants.MODULE_STATUS_DRAFTING.equals(status))
            throw new NopException(ERR_MODULE_NOT_DRAFTING).param("status", status);

        checkDataAuth(BizConstants.METHOD_UPDATE, module, context);
        module.setStatus(_NopMetadataCoreConstants.MODULE_STATUS_RELEASED);
        dao().updateEntity(module);
        return module;
    }

    private long computeNextModuleVersion(OrmModel ormModel) {
        Object appNameObj = ormModel.prop_get(OrmModelConstants.EXT_APP_NAME);
        String appName = appNameObj != null ? String.valueOf(appNameObj) : null;
        String moduleId = appName != null ? appName.replace('-', '/') : "unknown";

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, moduleId));
        query.addOrderField(NopMetaModule.PROP_NAME_moduleVersion, true);

        NopMetaModule latest = dao().findFirstByQuery(query);
        if (latest == null || latest.getModuleVersion() == null)
            return 1L;
        return latest.getModuleVersion() + 1;
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
