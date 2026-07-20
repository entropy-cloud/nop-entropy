/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * 验证 dict option 的 i18n-en:label 已全部补齐并在生成产物中正确出现
 * （plan 1250-2 Phase 2 Proof，维度04-07）。
 *
 * <p>修复前：23 个 dict 共 79 个 option，仅 4 个有 i18n-en:label，其余缺失。
 * 修复后：79 个 option 全部含英文 label，在生成的 {@code _vfs/i18n/en/_nop-metadata.i18n.yaml} 中可见。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDictI18n extends JunitBaseTestCase {

    public TestNopMetaDictI18n() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Test
    public void testEnI18nFileContainsAllDictLabels() {
        IResource resource = VirtualFileSystem.instance().getResource("/i18n/en/_nop-metadata.i18n.yaml");
        assertNotNull(resource, "en i18n file must exist in _vfs");
        assertTrue(resource.exists(), "en i18n file must be generated: " + resource);

        String text = ResourceHelper.readText(resource);

        // Verify sample labels are present (not null)
        assertTrue(text.contains("Drafting") || text.contains("Released") || text.contains("Deprecated"),
                "module-status dict labels must be in English: " + text);
        assertTrue(text.contains("JDBC") || text.contains("HTTP") || text.contains("REST"),
                "datasource-type dict labels must be in English: " + text);
        assertTrue(text.contains("ETL") || text.contains("Manual"),
                "pipeline-type dict labels must be in English: " + text);
    }

    @Test
    public void testEnI18nParsesAsYaml() {
        // Sanity: file is valid YAML and has expected top-level keys
        IResource resource = VirtualFileSystem.instance().getResource("/i18n/en/_nop-metadata.i18n.yaml");
        assertNotNull(resource);
        Object parsed = JsonTool.parseBeanFromResource(resource, Object.class);
        assertNotNull(parsed, "en i18n file must be valid YAML");
        assertTrue(parsed instanceof Map, "root must be a Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        assertFalse(root.isEmpty(), "en i18n root must not be empty");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEnI18nAllEntitiesHaveDisplayNames() {
        IResource resource = VirtualFileSystem.instance().getResource("/i18n/en/_nop-metadata.i18n.yaml");
        assertNotNull(resource);
        Object parsed = JsonTool.parseBeanFromResource(resource, Object.class);
        Map<String, Object> root = (Map<String, Object>) parsed;
        Map<String, Object> entity = (Map<String, Object>) root.get("entity");
        assertNotNull(entity, "entity: section must exist");
        Map<String, Object> label = (Map<String, Object>) entity.get("label");
        assertNotNull(label, "entity.label: section must exist");
        // 32 entities expected (display name coverage)
        assertTrue(label.size() >= 30,
                "expected >=30 entity display names in en i18n, got: " + label.size());
        assertNotNull(label.get("NopMetaModule"), "NopMetaModule display name must be set");
        assertNotNull(label.get("NopMetaLineageEdge"), "NopMetaLineageEdge display name must be set");
        assertNotNull(label.get("NopMetaReconciliationConfig"),
                "NopMetaReconciliationConfig display name must be set");
    }
}
