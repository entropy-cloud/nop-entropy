package io.nop.orm.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2552 切片束 a（orm-model）钉：
 * pin-a（C1-016）：dependByOtherEntity volatile（HB 闭合；:61 既有零弱化钉为本批义务）；
 * pin-b（C1-017）：冻结后 addProp 抛且 props 未污染（守卫前置）；
 * pin-c（C1-019）：OrmModel volatile 守卫 + 双检（结构钉）；OrmEntityModel.inited volatile；
 * pin-d（C1-018）：LazyLoadOrmModel 短名冲突预检先于写入（结构钉——变异红：回到先写后检形态红）。
 */
public class TestOrmModelSliceA {

    @Test
    public void pin_a_dependByOtherEntityVolatile() throws Exception {
        Field f = OrmEntityModel.class.getDeclaredField("dependByOtherEntity");
        assertTrue(Modifier.isVolatile(f.getModifiers()),
                "C1-016: dependByOtherEntity must be volatile (direction-one HB fix)");
        // setDependByOtherEntity 豁免守卫（sanctioned 运行期写点——裁定 J/I）
        OrmEntityModel model = new OrmEntityModel();
        model.freeze(true);
        model.setDependByOtherEntity(true); // 不得抛
        assertTrue(model.isDependByOtherEntity());
    }

    @Test
    public void pin_b_frozenAddPropGuardFirst() {
        OrmEntityModel model = new OrmEntityModel();
        model.freeze(true);
        // 守卫前置：冻结后 addProp 抛（先污染后守卫形态=变异红——null prop 在守卫处被拒，
        // 不进入 props 写入）
        assertThrows(Exception.class, () -> model.addProp(null));
        // props 污染断言：反射检查字段未新建（原形态先 put 再守卫会留下非空 props）
        try {
            Field f = OrmEntityModel.class.getDeclaredField("props");
            f.setAccessible(true);
            assertEquals(null, f.get(model), "props must not be polluted when guard rejects");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void pin_c_initAtomicForm() throws Exception {
        assertTrue(Modifier.isVolatile(OrmEntityModel.class.getDeclaredField("inited").getModifiers()),
                "C1-019: OrmEntityModel.inited must be volatile");
        String src = Files.readString(Paths.get("src/main/java/io/nop/orm/model/OrmModel.java"));
        assertTrue(src.contains("private volatile boolean inited;"),
                "OrmModel must carry volatile init guard");
        assertTrue(src.contains("if (inited)\n            return;\n        synchronized (this) {"),
                "OrmModel.init must be double-checked (bare synchronized gives no HB to lock-free readers)");
    }

    @Test
    public void pin_d_lazyPutPrecheckBeforeWrite() throws Exception {
        String src = Files.readString(Paths.get(
                "src/main/java/io/nop/orm/model/lazy/LazyLoadOrmModel.java"));
        int putStart = src.indexOf("void putEntityModel(IEntityModel entityModel) {");
        int throwAt = src.indexOf("ERR_ORM_MODEL_DUPLICATE_ENTITY_SHORT_NAME", putStart);
        int tableWrite = src.indexOf("entityModelByTableMap.put(", putStart);
        assertTrue(putStart >= 0 && throwAt > putStart && tableWrite > throwAt,
                "C1-018: short-name conflict precheck must precede index writes (no half-registered residue)");
        // 变异红形态：若回到先写 table/name 再检（tableWrite < throwAt），本断言红
    }
}
