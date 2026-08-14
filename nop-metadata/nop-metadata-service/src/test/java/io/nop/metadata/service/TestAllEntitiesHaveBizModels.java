package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IEntityModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证每个实体类都有对应的 BizModel 类。
 *
 * <p>F19（plan 2026-08-14-1448-1）：实体清单从 ORM 模型运行时注册表动态发现
 * （{@link IOrmTemplate#getOrmModel()} + {@link IEntityModel}），替代原手写硬编码 list。
 * 新增 ORM entity 后无需手工编辑此测试即被自动覆盖——守卫不可被"忘记在此追加"绕过。
 *
 * <p>Anti-Hollow 接线验证：发现机制读取真实 ORM 注册表（非固定 list）——
 * (1) 过滤限定到 {@code io.nop.metadata.dao.entity} 包（排除框架/其他模块 entity）；
 * (2) 过滤掉 {@code _gen} 子包下划线前缀生成基类（非 entity、无 BizModel，否则误报 missing）；
 * (3) sanity 断言发现实体数 ≥ 已知基线 39，防止动态发现静默返回空集。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestAllEntitiesHaveBizModels extends JunitBaseTestCase {

    /** 已知 ORM entity 基线数（{@code nop-metadata/model/nop-metadata.orm.xml}）。新增 entity 时同步上调。 */
    private static final int KNOWN_ENTITY_BASELINE = 39;

    private static final String ENTITY_PACKAGE = "io.nop.metadata.dao.entity.";

    private static final String BIZMODEL_PACKAGE = "io.nop.metadata.service.entity.";

    @Inject
    IOrmTemplate orm;

    @Test
    public void testAllEntitiesHaveBizModels() {
        List<Class<?>> entities = discoverEntities();

        // Sanity：动态发现非空集且 ≥ 已知基线（防止注册表读取接线断裂导致守卫静默失效）
        assertTrue(entities.size() >= KNOWN_ENTITY_BASELINE,
                "Dynamic discovery must find >= " + KNOWN_ENTITY_BASELINE
                        + " nop-metadata entities, but found " + entities.size()
                        + " — discovery wiring is broken (returned empty/partial set)");
        assertFalse(entities.isEmpty(), "Discovered entity set must not be empty");

        List<String> missing = new ArrayList<>();
        for (Class<?> entityClass : entities) {
            String bizClassName = BIZMODEL_PACKAGE + entityClass.getSimpleName() + "BizModel";
            try {
                Class<?> bizClass = Class.forName(bizClassName);
                assertNotNull(bizClass, "BizModel class must exist for entity: " + entityClass.getSimpleName());
            } catch (ClassNotFoundException e) {
                missing.add(entityClass.getSimpleName());
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError("Missing BizModel for entities: " + String.join(", ", missing));
        }
    }

    /**
     * 从 ORM 模型运行时注册表动态发现 nop-metadata 实体类。
     *
     * <p>过滤规则：(1) 仅 {@code io.nop.metadata.dao.entity} 直接包（排除框架/其他模块 entity）；
     * (2) 排除 {@code _gen} 子包生成基类（{@code io.nop.metadata.dao.entity._gen._NopMetaXxx}，
     * 下划线前缀，非 ORM entity、无对应 BizModel）；
     * (3) defensive 跳过任何下划线前缀类（覆盖生成基类漏网场景）。
     */
    private List<Class<?>> discoverEntities() {
        List<Class<?>> entities = new ArrayList<>();
        for (IEntityModel em : orm.getOrmModel().getEntityModels()) {
            String name = em.getName();
            if (name == null || !name.startsWith(ENTITY_PACKAGE)) {
                continue;
            }
            // 排除 _gen 子包生成基类
            if (name.contains("._gen.")) {
                continue;
            }
            String className = em.getClassName();
            if (className == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(className);
                // Defensive：跳过任何下划线前缀生成基类（_NopMeta*）
                if (clazz.getSimpleName().startsWith("_")) {
                    continue;
                }
                entities.add(clazz);
            } catch (ClassNotFoundException e) {
                throw new AssertionError("Entity class not on classpath: " + className, e);
            }
        }
        return entities;
    }
}
