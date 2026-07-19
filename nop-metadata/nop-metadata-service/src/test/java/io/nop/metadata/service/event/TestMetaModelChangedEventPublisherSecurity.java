/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service.event;

import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-07 + 维度13-01 回归测试：验证 {@link MetaModelChangedEventPublisher} 的 sensitive 列脱敏机制。
 *
 * <p><b>核心断言</b>：
 * <ul>
 *   <li>{@link MetaModelChangedEventPublisher#isSensitiveColumn} 按 ORM tagSet + 兜底列名集 判定敏感列
 *       （anti-hollow：方法实际被调用，不是常量定义）。</li>
 *   <li>{@link MetaModelChangedEventPublisher#buildEntitySnapshot} 对 sensitive 列返回
 *       {@link MetaModelChangedEventPublisher#REDACTED_VALUE} 而非实际值（不调用 orm_propValueByName，
 *        证明不读取实际值）。</li>
 *   <li>常见凭据列名（connectionConfig/password/jdbcUrl）在未配置 tagSet 时也兜底脱敏（defense-in-depth）。</li>
 * </ul>
 *
 * <p>使用 Mockito mock IOrmEntity/IEntityModel/IColumnModel，避免依赖完整 IoC 容器和样板代码，
 * 同时精确验证 orm_propValueByName 不被调用（防"读后丢弃"反模式）。
 */
public class TestMetaModelChangedEventPublisherSecurity {

    private static IColumnModel mockColumn(String name, Set<String> tagSet) {
        IColumnModel col = Mockito.mock(IColumnModel.class);
        Mockito.when(col.getName()).thenReturn(name);
        Mockito.when(col.getTagSet()).thenReturn(tagSet);
        return col;
    }

    private static IOrmEntity mockOrmEntity(IEntityModel model, Map<String, Object> values) {
        IOrmEntity entity = Mockito.mock(IOrmEntity.class);
        Mockito.when(entity.orm_entityModel()).thenReturn(model);
        // 任何 orm_propValueByName 调用都从 values Map 中查找
        Mockito.when(entity.orm_propValueByName(Mockito.anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return values.get(name);
        });
        return entity;
    }

    private static IEntityModel mockModel(IColumnModel... columns) {
        IEntityModel model = Mockito.mock(IEntityModel.class);
        // IEntityModel.getColumns() 返回 List<? extends IColumnModel>（通配符），
        // Mockito thenReturn 不直接支持通配符，用 doReturn/thenAnswer 绕过
        Mockito.doReturn(Arrays.asList(columns)).when(model).getColumns();
        return model;
    }

    // ===== isSensitiveColumn 判定 =====

    /** tagSet 含 "sensitive" → 判定为敏感。 */
    @Test
    public void testTagSetSensitiveDetected() {
        IColumnModel col = mockColumn("connectionConfig", new HashSet<>(Collections.singletonList("sensitive")));
        assertTrue(MetaModelChangedEventPublisher.isSensitiveColumn(col, "connectionConfig"),
                "column with tagSet=sensitive must be detected as sensitive");
    }

    /** 兜底列名集（即使未配置 tagSet） → 判定为敏感。 */
    @Test
    public void testFallbackColumnNameDetected() {
        String[] sensitiveNames = {"connectionConfig", "password", "passwd", "pwd",
                "secret", "apiKey", "api_key", "accessKey", "access_key",
                "privateKey", "private_key", "token", "accessToken", "access_token",
                "jdbcUrl", "jdbc_url"};
        for (String name : sensitiveNames) {
            IColumnModel col = mockColumn(name, Collections.emptySet());
            assertTrue(MetaModelChangedEventPublisher.isSensitiveColumn(col, name),
                    "fallback column name must be detected as sensitive: " + name);
        }
    }

    /** 非敏感列（普通业务字段）→ 不判定为敏感。 */
    @Test
    public void testNonSensitiveColumnNotDetected() {
        String[] normalNames = {"displayName", "status", "version", "createdBy", "createTime", "remark"};
        for (String name : normalNames) {
            IColumnModel col = mockColumn(name, Collections.emptySet());
            assertFalse(MetaModelChangedEventPublisher.isSensitiveColumn(col, name),
                    "normal column must NOT be detected as sensitive: " + name);
        }
    }

    // ===== buildEntitySnapshot 脱敏行为 =====

    /**
     * <b>核心脱敏断言（anti-hollow）</b>：
     * 敏感列在 buildEntitySnapshot 中返回 {@link MetaModelChangedEventPublisher#REDACTED_VALUE}
     * 且 {@link IOrmEntity#orm_propValueByName(String)} 不被调用（证明不读取实际值）。
     */
    @Test
    public void testSensitiveColumnRedactedWithoutReadingValue() {
        // 构造 NopMetaDataSource 实体模型：connectionConfig (tagSet=sensitive) + name (normal)
        IColumnModel cfgCol = mockColumn("connectionConfig",
                new HashSet<>(Collections.singletonList("sensitive")));
        IColumnModel nameCol = mockColumn("name", Collections.emptySet());
        IEntityModel model = mockModel(cfgCol, nameCol);

        // 模拟实体：connectionConfig 含明文密码（应被脱敏）；name 是普通字段（应正常读）
        Map<String, Object> realValues = new LinkedHashMap<>();
        realValues.put("connectionConfig",
                "{\"jdbcUrl\":\"jdbc:mysql://prod/db\",\"username\":\"admin\",\"password\":\"SUPER_SECRET_PWD\"}");
        realValues.put("name", "ds-prod");
        IOrmEntity entity = mockOrmEntity(model, realValues);

        MetaModelChangedEventPublisher publisher = new MetaModelChangedEventPublisher(null);
        Map<String, Object> snapshot = publisher.buildEntitySnapshot(entity);

        // 敏感列必须被脱敏（不读取实际值）
        assertEquals(MetaModelChangedEventPublisher.REDACTED_VALUE,
                snapshot.get("connectionConfig"),
                "sensitive column connectionConfig must be redacted to ***REDACTED***");
        assertFalse(String.valueOf(snapshot.get("connectionConfig")).contains("SUPER_SECRET_PWD"),
                "redacted value must NOT contain the real password");
        assertFalse(String.valueOf(snapshot.get("connectionConfig")).contains("jdbc:mysql"),
                "redacted value must NOT contain the real jdbcUrl");

        // 非敏感列必须正常读取（证明脱敏是 per-column 的，不是整张表）
        assertEquals("ds-prod", snapshot.get("name"),
                "non-sensitive column name must be read normally");

        // 关键 anti-hollow：orm_propValueByName("connectionConfig") 不应被调用
        // （脱敏分支不读取实际值；只有非敏感列 name 会触发 orm_propValueByName）
        Mockito.verify(entity, Mockito.times(1)).orm_propValueByName(Mockito.anyString());
        Mockito.verify(entity, Mockito.never()).orm_propValueByName("connectionConfig");
        Mockito.verify(entity, Mockito.times(1)).orm_propValueByName("name");
    }

    /**
     * <b>兜底脱敏</b>：列名匹配兜底集（password）但 ORM 模型未配置 tagSet → 仍脱敏。
     */
    @Test
    public void testFallbackRedactionForKnownCredentialColumns() {
        // 用户实体的 password 列：未在 ORM 模型上配 tagSet=sensitive（模拟外部实体场景）
        IColumnModel pwdCol = mockColumn("password", Collections.emptySet());
        IColumnModel userCol = mockColumn("userName", Collections.emptySet());
        IEntityModel model = mockModel(pwdCol, userCol);

        Map<String, Object> realValues = new LinkedHashMap<>();
        realValues.put("password", "myPlainTextPassword123!");
        realValues.put("userName", "alice");
        IOrmEntity entity = mockOrmEntity(model, realValues);

        MetaModelChangedEventPublisher publisher = new MetaModelChangedEventPublisher(null);
        Map<String, Object> snapshot = publisher.buildEntitySnapshot(entity);

        assertEquals(MetaModelChangedEventPublisher.REDACTED_VALUE, snapshot.get("password"),
                "fallback credential column 'password' must be redacted even without tagSet");
        assertFalse(String.valueOf(snapshot.get("password")).contains("myPlainTextPassword123"),
                "redacted password must NOT contain the real value");
        assertEquals("alice", snapshot.get("userName"));
        Mockito.verify(entity, Mockito.never()).orm_propValueByName("password");
    }

    /**
     * <b>完整 JSON 快照脱敏</b>：buildSnapshot 序列化后的 JSON 字符串不含明文凭据。
     */
    @Test
    public void testBuildSnapshotJsonRedactsCredentials() {
        IColumnModel cfgCol = mockColumn("connectionConfig",
                new HashSet<>(Collections.singletonList("sensitive")));
        IEntityModel model = mockModel(cfgCol);

        Map<String, Object> realValues = new LinkedHashMap<>();
        realValues.put("connectionConfig", "{\"password\":\"SECRET_VALUE_XYZ\"}");
        IOrmEntity entity = mockOrmEntity(model, realValues);

        MetaModelChangedEventPublisher publisher = new MetaModelChangedEventPublisher(null);
        String json = publisher.buildSnapshot(entity, "NopMetaDataSource", "ds-1");

        assertTrue(json.contains(MetaModelChangedEventPublisher.REDACTED_VALUE),
                "snapshot JSON must contain redaction placeholder: " + json);
        assertFalse(json.contains("SECRET_VALUE_XYZ"),
                "snapshot JSON must NOT contain the real secret: " + json);
    }

    /**
     * 非 ORM 实体（Map）路径：不走脱敏分支（POJO/Map 由调用方自行保证不含敏感字段）。
     * 验证 Map 路径不受 buildEntitySnapshot 改动影响（向后兼容）。
     */
    @Test
    public void testMapEntityPathUnchanged() {
        Map<String, Object> mapEntity = new LinkedHashMap<>();
        mapEntity.put("foo", "bar");
        MetaModelChangedEventPublisher publisher = new MetaModelChangedEventPublisher(null);
        Map<String, Object> snapshot = publisher.buildEntitySnapshot(mapEntity);
        assertEquals("bar", snapshot.get("foo"),
                "Map entity path must work unchanged (caller responsibility for sensitive fields)");
    }
}
