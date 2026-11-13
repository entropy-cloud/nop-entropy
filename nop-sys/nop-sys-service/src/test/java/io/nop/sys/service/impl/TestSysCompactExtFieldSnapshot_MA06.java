package io.nop.sys.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.support.IOrmCompactExtFieldSupport;
import io.nop.sys.dao.entity.NopSysCompactExtField;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-06 兄弟站点（W-01/C1-004 搭车消化，plan 2525 裁定⑦）：SysCompactExtFieldHelper 的
 * 双 volatile 字段（positions/configs）两行赋值无法原子替换——并发读在纳秒级窗内拿到
 * 新 positions × 旧 configs 的代际错配（默认值读到 null）。修复 = 双 map 合一不可变
 * Snapshot record，单 volatile 引用发布，读点单次读取。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysCompactExtFieldSnapshot_MA06 extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    static class TwoGenEntity implements IOrmCompactExtFieldSupport {
        private final String extFlags;

        TwoGenEntity(String extFlags) {
            this.extFlags = extFlags;
        }

        @Override
        public String orm_entityName() {
            return "snapshot-entity";
        }

        @Override
        public String getExtFlags() {
            return extFlags;
        }

        @Override
        public void setExtFlags(String flags) {
        }
    }

    private void saveField(String entityName, String propName, int position, String defaultValue) {
        NopSysCompactExtField field = new NopSysCompactExtField();
        field.setEntityName(entityName);
        field.setPropName(propName);
        field.setPosition(position);
        field.setDefaultValue(defaultValue);
        daoProvider.daoFor(NopSysCompactExtField.class).saveEntityDirectly(field);
    }

    /**
     * 结构钉：快照必须为单 volatile 引用（不再存在双 volatile 字段），且为不可变 record。
     */
    @Test
    public void testSingleSnapshotFieldPublishedVolatile() throws Exception {
        Path source = Path.of("src/main/java/io/nop/sys/service/impl/SysCompactExtFieldHelper.java");
        String code = Files.readString(source);

        assertTrue(code.contains("private volatile ExtFieldSnapshot snapshot"),
                "C1-004: positions+configs must be published as ONE immutable snapshot reference");
        assertFalse(code.contains("private volatile Map<String, Map<String, Integer>> entityFieldPositions"),
                "C1-004: the two-volatile-fields form must be gone — two assignments cannot be atomic"
                        + " and readers may pair new positions with stale configs (null default values)");
        assertTrue(code.contains("record ExtFieldSnapshot"),
                "C1-004: the snapshot must be an immutable record (fields never reassigned after publish)");
    }

    /**
     * 代际一致性行为钉：交替发布两代配置（同字段不同默认值），并发读 getExtValues 的
     * (extA, extB) 配对必须同代际——不允许 ("1","2")/("2","1") 混代配对（W-01 缺陷形态）。
     */
    @Test
    public void testReadersNeverSeeMixedGenerationPairs() throws Exception {
        saveField("snapshot-entity", "extA", 0, "1");
        saveField("snapshot-entity", "extB", 1, "1");

        SysCompactExtFieldHelper helper = new SysCompactExtFieldHelper();
        helper.daoProvider = daoProvider;
        helper.init();

        IEntityDao<NopSysCompactExtField> dao = daoProvider.daoFor(NopSysCompactExtField.class);

        AtomicInteger mixedPairs = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch stop = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            while (stop.getCount() > 0) {
                Map<String, String> values = helper.getExtValues(new TwoGenEntity("  "));
                String a = values.get("extA");
                String b = values.get("extB");
                reads.incrementAndGet();
                if (a != null && b != null && !a.equals(b))
                    mixedPairs.incrementAndGet(); // 混代配对 = W-01 缺陷形态
            }
        }, "snapshot-reader");
        reader.start();

        for (int gen = 2; gen <= 200; gen++) {
            String defaultValue = String.valueOf(gen % 2 == 0 ? 2 : 1);
            List<NopSysCompactExtField> fields = dao.findAll();
            for (NopSysCompactExtField field : fields) {
                field.setDefaultValue(defaultValue);
                dao.updateEntityDirectly(field);
            }
            helper.refreshCache();
        }

        stop.countDown();
        reader.join(5000);

        assertTrue(reads.get() > 0, "reader must have executed");
        assertEquals(0, mixedPairs.get(),
                "C1-004/W-01: positions and configs read by one call must be from the same snapshot"
                        + " generation — mixed pairs yield ('1','2')-style inconsistent default values");
    }
}
