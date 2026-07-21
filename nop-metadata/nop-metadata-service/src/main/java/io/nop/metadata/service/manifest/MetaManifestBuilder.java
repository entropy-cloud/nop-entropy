/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.manifest;

import io.nop.commons.util.StringHelper;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityRelation;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Manifest 构建服务：从已导入的逻辑元数据聚合 nodes/sources/parentMap/childMap，生成自包含 JSON 快照。
 *
 * <p>纯转换逻辑，不直接访问数据库——所有数据由调用方（BizModel）加载后传入。设计规格见
 * {@code ai-dev/design/nop-metadata/05-metadata-import.md} §三/§五：
 * <ul>
 *   <li>首版 nodes 仅 entity 节点；uniqueId = {@code entity.<moduleId 归一化>.<简单类名>}（D4）。</li>
 *   <li>parentMap/childMap 首版仅来自 MetaEntityRelation，边为 entity→entity（D3）。</li>
 *   <li>relation→边 resolution：refEntityName(className) → 全局反查 entity → module → uniqueId（D4）。</li>
 *   <li>跨模块/未导入引用记为 {@code unresolved:<className>}（不静默丢边）。</li>
 *   <li>无关系的节点 parentMap/childMap 显式置空数组（不静默跳过）。</li>
 * </ul>
 */
public class MetaManifestBuilder {

    /**
     * @param module               目标模块版本（提供 moduleId/moduleVersion）
     * @param fullOrmModel         该模块的 full ORM 模型（isDelta=false）；仅用于存在性校验
     * @param moduleEntities       该模块 full 模型下的实体集合
     * @param moduleRelations      该模块实体的关系集合（来自 full 模型）
     * @param classNameToModuleId  全局 className → moduleId 反查索引（用于 relation resolution，跨模块）。
     *                             key=实体 className，value=该实体所属模块的业务 moduleId（含 `/`）。
     * @param nopMetadataVersion   生成时的平台版本
     * @param manifestVersion      本次快照的 manifestVersion
     * @param now                  生成时间
     */
    public ManifestBuildResult build(NopMetaModule module,
                                     NopMetaOrmModel fullOrmModel,
                                     List<NopMetaEntity> moduleEntities,
                                     List<NopMetaEntityRelation> moduleRelations,
                                     Map<String, String> classNameToModuleId,
                                     String nopMetadataVersion,
                                     long manifestVersion,
                                     Date now) {
        if (module == null)
            throw new NopMetadataException(NopMetadataErrors.ERR_MANIFEST_MODULE_NULL)
                    .param(NopMetadataErrors.ARG_META_MODULE_ID, "null");
        if (fullOrmModel == null)
            throw new NopMetadataException(NopMetadataErrors.ERR_MANIFEST_ORM_MODEL_NULL)
                    .param(NopMetadataErrors.ARG_META_MODULE_ID,
                            module != null ? module.getModuleId() : "null");

        String moduleId = module.getModuleId();
        String normalizedModuleId = normalizeModuleId(moduleId);

        // 本模块实体的 uniqueId 索引（className → uniqueId），供 relation resolution 在模块内命中
        Map<String, String> localClassToUnique = new LinkedHashMap<>();
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        Set<String> sourceQuerySpaces = new LinkedHashSet<>();

        for (NopMetaEntity e : moduleEntities) {
            String uniqueId = entityUniqueId(normalizedModuleId, e);
            nodes.put(uniqueId, buildEntityNode(uniqueId, e));
            if (e.getClassName() != null)
                localClassToUnique.put(e.getClassName(), uniqueId);
            if (e.getQuerySpace() != null && !e.getQuerySpace().isEmpty())
                sourceQuerySpaces.add(e.getQuerySpace());
        }

        // 依赖图：parentMap/childMap。本模块每个节点显式建空列表（无关系的节点也保留空数组，不静默跳过）
        Map<String, List<String>> parentMap = new LinkedHashMap<>();
        Map<String, List<String>> childMap = new LinkedHashMap<>();
        for (String uid : nodes.keySet()) {
            parentMap.put(uid, new ArrayList<>());
            childMap.put(uid, new ArrayList<>());
        }

        int unresolvedCount = 0;
        // 本模块实体的 className → uniqueId（本模块视角），用于定位 relation 的 owner 节点
        for (NopMetaEntityRelation rel : moduleRelations) {
            String ownerUniqueId = findOwnerUniqueId(rel.getMetaEntityId(), moduleEntities, normalizedModuleId);
            if (ownerUniqueId == null) {
                // relation 的 owner 不在本模块节点集中（数据不一致）——不静默跳过，记 unresolved
                unresolvedCount++;
                continue;
            }
            String refClassName = rel.getRefEntityName();
            String targetUniqueId = resolveRef(refClassName, localClassToUnique, classNameToModuleId);
            if (targetUniqueId == null) {
                // dangling：跨模块/未导入引用。显式保留 unresolved:<className>，不丢边
                targetUniqueId = "unresolved:" + (refClassName != null ? refClassName : "null");
                unresolvedCount++;
            }
            // 边方向：owner 依赖 target（owner 引用 target）
            //   parentMap[owner] 追加 target（owner 的上游）
            //   childMap[target]  追加 owner （target 的下游）
            addEdge(parentMap, ownerUniqueId, targetUniqueId);
            // target 可能不在本模块节点集（跨模块/unresolved），childMap 需为它建条目
            childMap.computeIfAbsent(targetUniqueId, k -> new ArrayList<>()).add(ownerUniqueId);
        }

        Map<String, Object> content = new LinkedHashMap<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("moduleId", moduleId);
        metadata.put("moduleVersion", module.getModuleVersion());
        metadata.put("manifestVersion", manifestVersion);
        // generatedAt 序列化为 ISO-8601 字符串：严格 JSON 序列化（JsonTool.stringify）不接受裸 java.util.Date
        metadata.put("generatedAt", formatIso(now));
        metadata.put("nopMetadataVersion", nopMetadataVersion);
        content.put("metadata", metadata);
        content.put("nodes", nodes);
        content.put("sources", buildSources(sourceQuerySpaces));
        content.put("parentMap", parentMap);
        content.put("childMap", childMap);

        return new ManifestBuildResult(content, unresolvedCount, nodes.size());
    }

    /**
     * moduleId slash→dot 归一化（D4）：{@code nop/auth} → {@code nop.auth}。
     */
    static String normalizeModuleId(String moduleId) {
        if (moduleId == null)
            return "";
        return moduleId.replace('/', '.');
    }

    /**
     * ISO-8601（UTC）格式化，避免把裸 java.util.Date 放入待序列化的 JSON Map。
     */
    private static String formatIso(Date date) {
        if (date == null)
            return null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    /**
     * 节点 uniqueId（D4）：{@code entity.<normalizedModuleId>.<简单类名>}。
     * 简单类名取自 className 最后一段（entityName 列存的是 full className，直接拼会重复）。
     */
    static String entityUniqueId(String normalizedModuleId, NopMetaEntity e) {
        String simple = e.getClassName() != null
                ? StringHelper.simpleClassName(e.getClassName())
                : StringHelper.lastPart(e.getEntityName(), '.');
        return "entity." + normalizedModuleId + "." + simple;
    }

    private static Map<String, Object> buildEntityNode(String uniqueId, NopMetaEntity e) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("uniqueId", uniqueId);
        node.put("resourceType", "entity");
        node.put("name", e.getClassName() != null
                ? StringHelper.simpleClassName(e.getClassName())
                : e.getEntityName());
        node.put("entityName", e.getEntityName());
        node.put("className", e.getClassName());
        node.put("tableName", e.getTableName());
        node.put("displayName", e.getDisplayName());
        node.put("tagSet", e.getTagSet());
        node.put("querySpace", e.getQuerySpace());
        return node;
    }

    private static Map<String, Object> buildSources(Set<String> querySpaces) {
        Map<String, Object> sources = new LinkedHashMap<>();
        for (String qs : querySpaces) {
            Map<String, Object> src = new LinkedHashMap<>();
            src.put("name", qs);
            src.put("querySpace", qs);
            sources.put(qs, src);
        }
        return sources;
    }

    /**
     * 定位 relation owner 的 uniqueId。owner 通过 metaEntityId 匹配本模块实体。
     */
    private static String findOwnerUniqueId(String metaEntityId, List<NopMetaEntity> moduleEntities,
                                            String normalizedModuleId) {
        if (metaEntityId == null)
            return null;
        for (NopMetaEntity e : moduleEntities) {
            if (metaEntityId.equals(e.getMetaEntityId()))
                return entityUniqueId(normalizedModuleId, e);
        }
        return null;
    }

    /**
     * relation resolution（D4）：refEntityName(className) → 全局反查 entity → module → uniqueId。
     * 先查本模块局部索引，再查全局 classNameToModuleId。
     *
     * @return 目标节点 uniqueId，无法解析返回 null（由调用方记 unresolved）
     */
    private static String resolveRef(String refClassName,
                                     Map<String, String> localClassToUnique,
                                     Map<String, String> classNameToModuleId) {
        if (refClassName == null || refClassName.isEmpty())
            return null;
        // 本模块内命中
        String local = localClassToUnique.get(refClassName);
        if (local != null)
            return local;
        // 全局反查（跨模块）
        String ownerModuleId = classNameToModuleId.get(refClassName);
        if (ownerModuleId == null)
            return null;
        String normalized = normalizeModuleId(ownerModuleId);
        String simple = StringHelper.simpleClassName(refClassName);
        return "entity." + normalized + "." + simple;
    }

    private static void addEdge(Map<String, List<String>> map, String key, String value) {
        if (key == null || value == null)
            return;
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    /**
     * Manifest 构建结果：content（待 JSON 序列化的 Map）+ 统计信息。
     */
    public static final class ManifestBuildResult {
        private final Map<String, Object> content;
        private final int unresolvedCount;
        private final int nodeCount;

        public ManifestBuildResult(Map<String, Object> content, int unresolvedCount, int nodeCount) {
            this.content = content;
            this.unresolvedCount = unresolvedCount;
            this.nodeCount = nodeCount;
        }

        public Map<String, Object> getContent() {
            return content;
        }

        public int getUnresolvedCount() {
            return unresolvedCount;
        }

        public int getNodeCount() {
            return nodeCount;
        }
    }
}
