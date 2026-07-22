/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.entity;


import io.nop.api.core.time.CoreMetrics;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceDslNodeLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaModuleBiz;
import io.nop.metadata.core.dto.ImportOrmModelResultDTO;
import io.nop.metadata.service.SeedGlossaryData;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDict;
import io.nop.metadata.dao.entity.NopMetaDictItem;
import io.nop.metadata.dao.entity.NopMetaDomain;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaEntityIndex;
import io.nop.metadata.dao.entity.NopMetaEntityRelation;
import io.nop.metadata.dao.entity.NopMetaEntityUniqueKey;
import io.nop.metadata.dao.entity.NopMetaManifest;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.model.OrmModelImporter;
import io.nop.metadata.service.event.MetaModelChangedEventPublisher;
import io.nop.metadata.service.manifest.MetaManifestBuilder;
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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.nop.metadata.service.search.NopMetaSearchService;
import io.nop.search.api.SearchableDoc;
import io.nop.metadata.service.NopMetadataException;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@BizModel("NopMetaModule")
public class NopMetaModuleBizModel extends CrudBizModel<NopMetaModule> implements INopMetaModuleBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaModuleBizModel.class);


    /** 事件 entityType（架构基线 §2.8 D3）：发布事件时记录的实体类型名。 */
    static final String EVENT_ENTITY_TYPE = "NopMetaModule";

    @Inject
    protected NopMetaSearchService searchService;

    @InjectValue("@cfg:nop.metadata.platform-version|2.0.0-SNAPSHOT")
    protected String platformVersion;

    @Inject
    protected IDaoProvider daoProvider;

    /** 元数据变更事件发布 helper（架构基线 §2.8 D2，IoC bean）。 */
    @Inject
    protected MetaModelChangedEventPublisher eventPublisher;

    private final MetaManifestBuilder manifestBuilder = new MetaManifestBuilder();

    public NopMetaModuleBizModel() {
        setEntityName(NopMetaModule.class.getName());
    }

    /**
     * save override（架构基线 §2.8 D3）：通用 CRUD 路径的 CREATE/UPDATE 事件发布。
     *
     * <p>before 快照在 super.save 前按 PK 加载（null=CREATE，非 null=UPDATE）；事件行在 super.save
     * 成功后写入（避免幽灵事件）。per-op UUID 作为 transactionId。
     *
     * <p>注：本 override 覆盖通用 CRUD（UI/GraphQL/xbiz）；{@link #importOrmModel}/{@link #releaseModule}
     * 等关键 mutation action 自行调 helper（不经本 override），二者独立。
     */
    @Override
    public NopMetaModule save(@Name("data") Map<String, Object> data, IServiceContext context) {
        String id = data == null ? null : stringOf(data, NopMetaModule.PROP_NAME_metaModuleId);
        NopMetaModule before = id != null ? dao().getEntityById(id) : null;
        NopMetaModule saved = super.save(data, context);
        String entityType = EVENT_ENTITY_TYPE;
        String eventType = before == null
                ? _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_CREATED
                : _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_UPDATED;
        String afterSnapshot = eventPublisher.buildSnapshot(saved, entityType, saved.getMetaModuleId());
        String beforeSnapshot = before != null
                ? eventPublisher.buildSnapshot(before, entityType, saved.getMetaModuleId()) : null;
        eventPublisher.publishEventWithSnapshots(eventType, entityType, saved.getMetaModuleId(),
                saved.getModuleName(), MetaModelChangedEventPublisher.CHANGE_SOURCE_API,
                beforeSnapshot, afterSnapshot,
                MetaModelChangedEventPublisher.newTransactionId(), context);
        return saved;
    }

    /**
     * delete override（架构基线 §2.8 D3）：通用 CRUD 路径的 DELETE 事件发布。save 不覆盖 delete，DELETE 走独立 override。
     *
     * <p>before 快照在 super.delete 前按 PK 加载；事件行在 super.delete 成功后写入（避免幽灵事件）。
     * 若实体不存在（DELETE 已删）则不发事件（beforeSnapshot=null + 已删除无快照可记）。
     */
    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaModule before = dao().getEntityById(id);
        boolean deleted = super.delete(id, context);
        if (before != null) {
            String beforeSnapshot = eventPublisher.buildSnapshot(before, EVENT_ENTITY_TYPE, id);
            eventPublisher.publishEventWithSnapshots(
                    _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_DELETED,
                    EVENT_ENTITY_TYPE, id, before.getModuleName(),
                    MetaModelChangedEventPublisher.CHANGE_SOURCE_API,
                    beforeSnapshot, null,
                    MetaModelChangedEventPublisher.newTransactionId(), context);
        }
        return deleted;
    }

    private static String stringOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }

    @BizMutation
    public NopMetaModule importOrmModel(@Name("path") String path, IServiceContext context) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (resource == null || !resource.exists())
            throw new NopMetadataException(NopMetadataErrors.ERR_ORM_RESOURCE_NOT_FOUND).param("path", path);

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
        List<NopMetaEntity> entities = new ArrayList<>();
        List<NopMetaEntityField> fields = new ArrayList<>();
        List<NopMetaTable> tables = new ArrayList<>();
        persistModelGraph(importer, deltaModel, sourceContent, moduleId, true, entities, fields, tables);
        persistModelGraph(importer, fullModel, sourceContent, moduleId, false, entities, fields, tables);

        orm().flushSession();

        for (NopMetaEntity entity : entities) {
            searchService.addToIndex("MetaEntity", entity.getMetaEntityId(), toSearchableDoc(entity));
        }
        for (NopMetaEntityField field : fields) {
            searchService.addToIndex("MetaEntityField", field.getEntityFieldId(), toSearchableDoc(field));
        }
        for (NopMetaTable table : tables) {
            searchService.addToIndex("MetaTable", table.getMetaTableId(), toSearchableDoc(table));
        }

        // 元数据变更事件（架构基线 §2.8 D3）：导入是批量操作，主实体级记录 1 行 Module CREATED（changeSource=IMPORT），
        // 子实体细粒度事件 deferred。事件行在持久化成功后写入，避免幽灵事件。
        String txId = MetaModelChangedEventPublisher.newTransactionId();
        eventPublisher.publishEvent(
                _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_CREATED,
                EVENT_ENTITY_TYPE, moduleId, module.getModuleName(),
                MetaModelChangedEventPublisher.CHANGE_SOURCE_IMPORT,
                null, module, txId, context);
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
            LOG.warn("parseDeltaModel failed, falling back to full model as delta", e);
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
            LOG.warn("resolveBaseModuleId failed, baseModuleId set to null", e);
            return null;
        }
    }

    /**
     * 持久化一组模型记录：NopMetaOrmModel + 其下所有子实体（Entity/Field/Relation/UK/Index/Domain/Dict/Table）。
     * 由 isDelta 参数控制所有子实体的 isDelta 标记。
     */
    private void persistModelGraph(OrmModelImporter importer, OrmModel ormModel, String sourceContent,
                                   String moduleId, boolean isDelta,
                                   List<NopMetaEntity> entities, List<NopMetaEntityField> fields, List<NopMetaTable> tables) {
        NopMetaOrmModel ormModelEntity = importer.buildOrmModel(ormModel, sourceContent, isDelta);
        ormModelEntity.setMetaModuleId(moduleId);
        orm().save(ormModelEntity);
        String ormModelId = ormModelEntity.getOrmModelId();

        for (IEntityModel em : ormModel.getEntityModels()) {
            NopMetaEntity entity = importer.buildEntity(em, isDelta);
            entity.setOrmModelId(ormModelId);
            orm().save(entity);
            entities.add(entity);
            String entityId = entity.getMetaEntityId();

            for (IColumnModel col : em.getColumns()) {
                NopMetaEntityField field = importer.buildField(col, isDelta);
                field.setMetaEntityId(entityId);
                orm().save(field);
                fields.add(field);
            }

            for (NopMetaEntityRelation rel : importer.buildRelations(em, isDelta)) {
                rel.setMetaEntityId(entityId);
                orm().save(rel);
            }

            NopMetaTable table = importer.buildEntityTable(em);
            table.setMetaModuleId(moduleId);
            table.setBaseEntityId(entityId);
            orm().save(table);
            tables.add(table);

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
    public List<ImportOrmModelResultDTO> importOrmModels(@Name("paths") List<String> paths, IServiceContext context) {
        List<ImportOrmModelResultDTO> results = new ArrayList<>();
        if (paths == null)
            return results;

        for (String path : paths) {
            ImportOrmModelResultDTO result = new ImportOrmModelResultDTO();
            try {
                NopMetaModule module = importOrmModel(path, context);
                result.setMetaModuleId(module.getMetaModuleId());
                result.setModuleName(module.getModuleName());
                result.setSuccess(true);
            } catch (Exception e) {
                LOG.error("importOrmModels failed for path: {}", path, e);
                result.setSuccess(false);
                result.setError(toErrorMessage(e));
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
            throw new NopMetadataException(NopMetadataErrors.ERR_MODULE_NOT_FOUND).param("metaModuleId", metaModuleId);

        String status = module.getStatus();
        if (!_NopMetadataCoreConstants.MODULE_STATUS_DRAFTING.equals(status))
            throw new NopMetadataException(NopMetadataErrors.ERR_MODULE_NOT_DRAFTING).param("status", status);

        checkDataAuth(BizConstants.METHOD_UPDATE, module, context);

        // 元数据变更事件（架构基线 §2.8 D3）：版本发布主实体级记录 1 行 Module UPDATED（changeSource=UI）。
        // before 快照必须在变更前捕获（status=DRAFTING），after 为发布后（status=RELEASED）。
        // 事件行在持久化成功后写入，避免幽灵事件。
        String beforeSnapshot = eventPublisher.buildSnapshot(module, EVENT_ENTITY_TYPE, metaModuleId);

        module.setStatus(_NopMetadataCoreConstants.MODULE_STATUS_RELEASED);
        dao().updateEntity(module);
        orm().flushSession();

        String afterSnapshot = eventPublisher.buildSnapshot(module, EVENT_ENTITY_TYPE, metaModuleId);
        eventPublisher.publishEventWithSnapshots(
                _NopMetadataCoreConstants.CHANGE_EVENT_TYPE_ENTITY_UPDATED,
                EVENT_ENTITY_TYPE, metaModuleId, module.getModuleName(),
                MetaModelChangedEventPublisher.CHANGE_SOURCE_UI,
                beforeSnapshot, afterSnapshot,
                MetaModelChangedEventPublisher.newTransactionId(), context);

        new SeedGlossaryData().seedGlossaryTerms(daoProvider());

        return module;
    }

    /**
     * 生成模块元数据快照（Manifest）。参考 dbt Manifest 模式，聚合本模块已导入的逻辑元数据为自包含 JSON。
     *
     * <p>设计规格见 {@code ai-dev/design/nop-metadata/05-metadata-import.md} §三/§五：
     * <ul>
     *   <li>快照粒度（D1）：每模块版本一条 NopMetaManifest（关联 metaModuleId）。</li>
     *   <li>存储形态（D2）：单行 JSON CLOB（content 列 mediumtext + stdDomain json）。</li>
     *   <li>依赖图（D3）：首版仅来自 MetaEntityRelation，entity→entity 边。</li>
     *   <li>节点 id/边 resolution（D4）：uniqueId=entity.&lt;归一化moduleId&gt;.&lt;简单类名&gt;；
     *       relation refEntityName(className) → 全局反查 entity → module → uniqueId；
     *       跨模块/未导入引用记为 unresolved:&lt;className&gt;（不静默丢边）。</li>
     *   <li>无关系的节点 parentMap/childMap 显式空数组（不静默跳过）。</li>
     * </ul>
     *
     * <p>快速失败（不静默返回空 Manifest）：
     * <ul>
     *   <li>metaModuleId 不存在 → 抛 {@link #NopMetadataErrors.ERR_MODULE_NOT_FOUND}</li>
     *   <li>模块无 full ORM 模型（isDelta=false） → 抛 {@link #NopMetadataErrors.ERR_MODULE_FULL_MODEL_NOT_FOUND}</li>
     * </ul>
     *
     * @return 新生成的 NopMetaManifest（content 已写入 JSON）
     */
    @BizMutation
    public NopMetaManifest generateManifest(@Name("metaModuleId") String metaModuleId, IServiceContext context) {
        NopMetaModule module = dao().getEntityById(metaModuleId);
        if (module == null)
            throw new NopMetadataException(NopMetadataErrors.ERR_MODULE_NOT_FOUND).param("metaModuleId", metaModuleId);

        IEntityDao<NopMetaOrmModel> ormModelDao = daoFor(NopMetaOrmModel.class);
        IEntityDao<NopMetaEntity> entityDao = daoFor(NopMetaEntity.class);
        IEntityDao<NopMetaEntityRelation> relationDao = daoFor(NopMetaEntityRelation.class);
        IEntityDao<NopMetaManifest> manifestDao = daoFor(NopMetaManifest.class);

        // full ORM 模型（isDelta=false）。不存在则快速失败，不静默生成空快照
        QueryBean fullOrmQ = new QueryBean();
        fullOrmQ.addFilter(FilterBeans.eq(NopMetaOrmModel.PROP_NAME_metaModuleId, metaModuleId));
        fullOrmQ.addFilter(FilterBeans.eq(NopMetaOrmModel.PROP_NAME_isDelta, (byte) 0));
        NopMetaOrmModel fullOrmModel = ormModelDao.findFirstByQuery(fullOrmQ);
        if (fullOrmModel == null)
            throw new NopMetadataException(NopMetadataErrors.ERR_MODULE_FULL_MODEL_NOT_FOUND).param("metaModuleId", metaModuleId);

        String ormModelId = fullOrmModel.getOrmModelId();

        // 本模块 full 模型下的实体
        QueryBean entityQ = new QueryBean();
        entityQ.addFilter(FilterBeans.eq(NopMetaEntity.PROP_NAME_ormModelId, ormModelId));
        List<NopMetaEntity> moduleEntities = entityDao.findAllByQuery(entityQ);

        // 本模块实体的关系（用于 entity→entity 依赖图）
        List<NopMetaEntityRelation> moduleRelations = new ArrayList<>();
        if (!moduleEntities.isEmpty()) {
            List<String> entityIds = new ArrayList<>(moduleEntities.size());
            for (NopMetaEntity e : moduleEntities)
                entityIds.add(e.getMetaEntityId());
            QueryBean relQ = new QueryBean();
            TreeBean inFilter = FilterBeans.in(NopMetaEntityRelation.PROP_NAME_metaEntityId, entityIds);
            relQ.addFilter(inFilter);
            moduleRelations = relationDao.findAllByQuery(relQ);
        }

        // 全局 className → moduleId 反查索引（用于跨模块 relation resolution，D4）
        Map<String, String> classNameToModuleId = buildGlobalClassNameToModuleId();

        // manifestVersion：同模块版本下重新生成时递增（首次为 1）
        long manifestVersion = computeNextManifestVersion(manifestDao, metaModuleId);

        MetaManifestBuilder.ManifestBuildResult result = manifestBuilder.build(
                module, fullOrmModel, moduleEntities, moduleRelations,
                classNameToModuleId, platformVersion, manifestVersion, new Date());

        if (result.getUnresolvedCount() > 0) {
            LOG.warn("generateManifest produced {} unresolved relation reference(s) for metaModuleId={}",
                    result.getUnresolvedCount(), metaModuleId);
        }

        NopMetaManifest manifest = new NopMetaManifest();
        manifest.setMetaModuleId(metaModuleId);
        manifest.setManifestVersion(manifestVersion);
        manifest.setGeneratedAt(CoreMetrics.currentTimestamp());
        manifest.setNopMetadataVersion(platformVersion);
        manifest.setContent(JsonTool.stringify(result.getContent()));
        manifestDao.saveEntity(manifest);
        orm().flushSession();
        return manifest;
    }

    /**
     * 全局 className → moduleId 索引（D4 resolution 用）。
     * 遍历所有模块的 full ORM 模型下的实体，建立 className → 其所属模块业务 moduleId 的映射。
     * 首版全量加载可接受（元数据目录规模有限），性能不足后续加 className 索引。
     */
    private Map<String, String> buildGlobalClassNameToModuleId() {
        IEntityDao<NopMetaModule> moduleDao = daoFor(NopMetaModule.class);
        IEntityDao<NopMetaOrmModel> ormModelDao = daoFor(NopMetaOrmModel.class);
        IEntityDao<NopMetaEntity> entityDao = daoFor(NopMetaEntity.class);

        // metaModuleId → moduleId（业务标识）
        Map<String, String> moduleBizId = new HashMap<>();
        for (NopMetaModule m : moduleDao.findAll())
            moduleBizId.put(m.getMetaModuleId(), m.getModuleId());

        // ormModelId → metaModuleId（仅 full 模型）
        Map<String, String> ormModelToModule = new HashMap<>();
        QueryBean fullOrmQ = new QueryBean();
        fullOrmQ.addFilter(FilterBeans.eq(NopMetaOrmModel.PROP_NAME_isDelta, (byte) 0));
        for (NopMetaOrmModel om : ormModelDao.findAllByQuery(fullOrmQ))
            ormModelToModule.put(om.getOrmModelId(), om.getMetaModuleId());

        // className → moduleId
        Map<String, String> classNameToModuleId = new HashMap<>();
        QueryBean fullEntityQ = new QueryBean();
        fullEntityQ.addFilter(FilterBeans.eq(NopMetaEntity.PROP_NAME_isDelta, (byte) 0));
        for (NopMetaEntity e : entityDao.findAllByQuery(fullEntityQ)) {
            String metaModuleId = ormModelToModule.get(e.getOrmModelId());
            if (metaModuleId == null)
                continue;
            String moduleId = moduleBizId.get(metaModuleId);
            if (moduleId != null && e.getClassName() != null)
                classNameToModuleId.put(e.getClassName(), moduleId);
        }
        return classNameToModuleId;
    }

    private long computeNextManifestVersion(IEntityDao<NopMetaManifest> manifestDao, String metaModuleId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaManifest.PROP_NAME_metaModuleId, metaModuleId));
        q.addOrderField(NopMetaManifest.PROP_NAME_manifestVersion, true);
        NopMetaManifest latest = manifestDao.findFirstByQuery(q);
        if (latest == null || latest.getManifestVersion() == null)
            return 1L;
        return latest.getManifestVersion() + 1;
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
            throw new NopMetadataException(NopMetadataErrors.ERR_ORM_RESOURCE_READ_FAILED, e).param("path", resource.getPath());
        }
    }

    private static String toErrorMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getName();
    }

    private SearchableDoc toSearchableDoc(NopMetaEntity entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getMetaEntityId());
        doc.setName(entity.getEntityName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(truncate(entity.getRemark(), 500));
        doc.setContent(join(" ", entity.getEntityName(), entity.getClassName(), entity.getDisplayName(), entity.getTagSet(), entity.getRemark()));
        doc.setTagSet(Set.of("MetaEntity"));
        return doc;
    }

    private SearchableDoc toSearchableDoc(NopMetaEntityField entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getEntityFieldId());
        doc.setName(entity.getFieldName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(truncate(entity.getComment(), 500));
        doc.setContent(join(" ", entity.getFieldName(), entity.getColumnCode(), entity.getDisplayName(), entity.getComment()));
        doc.setTagSet(Set.of("MetaEntityField"));
        return doc;
    }

    private SearchableDoc toSearchableDoc(NopMetaTable entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getMetaTableId());
        doc.setName(entity.getTableName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(truncate(entity.getDescription(), 500));
        doc.setContent(join(" ", entity.getTableName(), entity.getDisplayName(), entity.getDescription()));
        doc.setTagSet(Set.of("MetaTable"));
        return doc;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private static String join(String delimiter, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                if (sb.length() > 0) sb.append(delimiter);
                sb.append(part);
            }
        }
        return sb.toString();
    }
}
