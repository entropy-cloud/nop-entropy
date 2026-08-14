package io.nop.metadata.service.manifest;

import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityRelation;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manifest 构建器单元测试（纯转换逻辑，不依赖 ORM session / IoC）。
 *
 * <p>覆盖 AR-09：邻接表去重（重复关系不产重复邻居）+ 自环过滤（owner==target 不出现）。
 *
 * <p>Anti-Hollow / 接线验证：通过 {@link MetaManifestBuilder#build} → 遍历 relations → {@code addEdge}
 * 完整运行时路径验证 parentMap 与 childMap 双向邻接表均去重 + 自环过滤。
 */
public class TestMetaManifestBuilder {

    private final MetaManifestBuilder builder = new MetaManifestBuilder();

    private static NopMetaModule module(String moduleId) {
        NopMetaModule m = new NopMetaModule();
        m.setModuleId(moduleId);
        m.setModuleVersion(1L);
        return m;
    }

    private static NopMetaOrmModel dummyOrmModel() {
        return new NopMetaOrmModel();
    }

    private static NopMetaEntity entity(String entityId, String className, String entityName) {
        NopMetaEntity e = new NopMetaEntity();
        e.setMetaEntityId(entityId);
        e.setClassName(className);
        e.setEntityName(entityName);
        return e;
    }

    private static NopMetaEntityRelation relation(String ownerEntityId, String refEntityName) {
        NopMetaEntityRelation r = new NopMetaEntityRelation();
        r.setMetaEntityId(ownerEntityId);
        r.setRefEntityName(refEntityName);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> getMap(Map<String, Object> content, String key) {
        return (Map<String, List<String>>) content.get(key);
    }

    // ===== AR-09：邻接表去重 =====

    /** 重复关系（owner→target 出现 2 次）→ manifest 邻接表无重复邻居。 */
    @Test
    public void duplicateRelationNoDuplicateEdge() {
        NopMetaModule mod = module("test/dedup");
        List<NopMetaEntity> entities = Arrays.asList(
                entity("e-owner", "io.test.Owner", "Owner"),
                entity("e-target", "io.test.Target", "Target"));

        // classNameToModuleId: both in this module
        Map<String, String> classNameToModuleId = new LinkedHashMap<>();
        classNameToModuleId.put("io.test.Owner", "test/dedup");
        classNameToModuleId.put("io.test.Target", "test/dedup");

        // 重复关系：owner → target 出现 2 次
        List<NopMetaEntityRelation> relations = Arrays.asList(
                relation("e-owner", "io.test.Target"),
                relation("e-owner", "io.test.Target"));

        MetaManifestBuilder.ManifestBuildResult result = builder.build(
                mod, dummyOrmModel(), entities, relations, classNameToModuleId, "1.0", 1L, new java.util.Date());

        Map<String, Object> content = result.getContent();
        Map<String, List<String>> parentMap = getMap(content, "parentMap");
        Map<String, List<String>> childMap = getMap(content, "childMap");

        String ownerUid = "entity.test.dedup.Owner";
        String targetUid = "entity.test.dedup.Target";

        // parentMap[owner] should contain target exactly once (dedup)
        List<String> ownerParents = parentMap.get(ownerUid);
        assertEquals(1, ownerParents.size(), "duplicate relation must not produce duplicate edge in parentMap");
        assertEquals(targetUid, ownerParents.get(0));

        // childMap[target] should contain owner exactly once (dedup)
        List<String> targetChildren = childMap.get(targetUid);
        assertEquals(1, targetChildren.size(), "duplicate relation must not produce duplicate edge in childMap");
        assertEquals(ownerUid, targetChildren.get(0));
    }

    // ===== AR-09：自环过滤 =====

    /** 自环（owner 引用自身）→ 不出现在邻接表中。 */
    @Test
    public void selfLoopFiltered() {
        NopMetaModule mod = module("test/selfloop");
        List<NopMetaEntity> entities = Collections.singletonList(
                entity("e-self", "io.test.Self", "Self"));

        Map<String, String> classNameToModuleId = new LinkedHashMap<>();
        classNameToModuleId.put("io.test.Self", "test/selfloop");

        // 自环：Self 引用 Self
        List<NopMetaEntityRelation> relations = Collections.singletonList(
                relation("e-self", "io.test.Self"));

        MetaManifestBuilder.ManifestBuildResult result = builder.build(
                mod, dummyOrmModel(), entities, relations, classNameToModuleId, "1.0", 1L, new java.util.Date());

        Map<String, Object> content = result.getContent();
        Map<String, List<String>> parentMap = getMap(content, "parentMap");
        Map<String, List<String>> childMap = getMap(content, "childMap");

        String selfUid = "entity.test.selfloop.Self";

        // parentMap[self] should be empty (self-loop filtered)
        assertTrue(parentMap.get(selfUid).isEmpty(),
                "self-loop must not appear in parentMap");

        // childMap[self] should be empty (self-loop filtered)
        assertTrue(childMap.get(selfUid).isEmpty(),
                "self-loop must not appear in childMap");
    }

    // ===== 接线验证：parentMap 与 childMap 双向均去重 + 自环过滤 =====

    /** 复合场景：重复关系 + 自环 + 正常关系 → 邻接表正确。 */
    @Test
    public void mixedDuplicateAndSelfLoopAndNormal() {
        NopMetaModule mod = module("test/mixed");
        List<NopMetaEntity> entities = Arrays.asList(
                entity("e-a", "io.test.A", "A"),
                entity("e-b", "io.test.B", "B"),
                entity("e-c", "io.test.C", "C"));

        Map<String, String> classNameToModuleId = new LinkedHashMap<>();
        classNameToModuleId.put("io.test.A", "test/mixed");
        classNameToModuleId.put("io.test.B", "test/mixed");
        classNameToModuleId.put("io.test.C", "test/mixed");

        List<NopMetaEntityRelation> relations = new ArrayList<>();
        // A → B (duplicate)
        relations.add(relation("e-a", "io.test.B"));
        relations.add(relation("e-a", "io.test.B"));
        // B → B (self-loop)
        relations.add(relation("e-b", "io.test.B"));
        // A → C (single)
        relations.add(relation("e-a", "io.test.C"));
        // C → A (normal)
        relations.add(relation("e-c", "io.test.A"));

        MetaManifestBuilder.ManifestBuildResult result = builder.build(
                mod, dummyOrmModel(), entities, relations, classNameToModuleId, "1.0", 1L, new java.util.Date());

        Map<String, Object> content = result.getContent();
        Map<String, List<String>> parentMap = getMap(content, "parentMap");
        Map<String, List<String>> childMap = getMap(content, "childMap");

        String uidA = "entity.test.mixed.A";
        String uidB = "entity.test.mixed.B";
        String uidC = "entity.test.mixed.C";

        // parentMap[A] = [B, C] (no duplicate B, no self-loop)
        List<String> aParents = parentMap.get(uidA);
        assertEquals(2, aParents.size(), "A parents must be [B, C] after dedup+selfloop");
        assertTrue(aParents.contains(uidB));
        assertTrue(aParents.contains(uidC));

        // parentMap[B] = [] (self-loop filtered)
        assertTrue(parentMap.get(uidB).isEmpty(), "B self-loop must be filtered in parentMap");

        // parentMap[C] = [A]
        assertEquals(1, parentMap.get(uidC).size());
        assertEquals(uidA, parentMap.get(uidC).get(0));

        // childMap[B] = [A] (duplicate A deduped, no self-loop)
        List<String> bChildren = childMap.get(uidB);
        assertEquals(1, bChildren.size(), "B children must be [A] after dedup");
        assertEquals(uidA, bChildren.get(0));

        // childMap[C] = [A]
        assertEquals(1, childMap.get(uidC).size());
        assertEquals(uidA, childMap.get(uidC).get(0));

        // childMap[A] = [C]
        assertEquals(1, childMap.get(uidA).size());
        assertEquals(uidC, childMap.get(uidA).get(0));

        // No self-loop entries anywhere
        assertFalse(parentMap.get(uidB).contains(uidB), "no self-loop in parentMap[B]");
        assertFalse(childMap.get(uidB).contains(uidB), "no self-loop in childMap[B]");
    }

    /** 正常单一关系不被误过滤。 */
    @Test
    public void singleNormalRelationNotFiltered() {
        NopMetaModule mod = module("test/normal");
        List<NopMetaEntity> entities = Arrays.asList(
                entity("e-owner", "io.test.Owner", "Owner"),
                entity("e-target", "io.test.Target", "Target"));

        Map<String, String> classNameToModuleId = new LinkedHashMap<>();
        classNameToModuleId.put("io.test.Owner", "test/normal");
        classNameToModuleId.put("io.test.Target", "test/normal");

        List<NopMetaEntityRelation> relations = Collections.singletonList(
                relation("e-owner", "io.test.Target"));

        MetaManifestBuilder.ManifestBuildResult result = builder.build(
                mod, dummyOrmModel(), entities, relations, classNameToModuleId, "1.0", 1L, new java.util.Date());

        Map<String, Object> content = result.getContent();
        Map<String, List<String>> parentMap = getMap(content, "parentMap");

        String ownerUid = "entity.test.normal.Owner";
        String targetUid = "entity.test.normal.Target";

        assertEquals(1, parentMap.get(ownerUid).size());
        assertEquals(targetUid, parentMap.get(ownerUid).get(0));
    }
}
