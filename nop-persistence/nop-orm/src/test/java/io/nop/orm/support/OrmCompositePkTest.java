package io.nop.orm.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmErrors;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OrmCompositePkTest {
    private static OrmModel ormModel;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        ormModel = (OrmModel) DslModelHelper.loadDslModel(
                VirtualFileSystem.instance().getResource("/nop/test/orm/app.orm.xml"));
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testEquals() {
        OrmCompositePk pk1 = new OrmCompositePk(Arrays.asList("a", "b"), new Object[]{1, 2});
        OrmCompositePk pk2 = new OrmCompositePk(Arrays.asList("a", "b"), new Object[]{1, 2});
        assertEquals(pk1.hashCode(), pk2.hashCode());
        assertEquals(pk1, pk2);
    }

    /**
     * id字符串中的"null"片段表示主键为null，主键不允许为null。
     * 必须抛出带实体上下文的NopException，而不是让构造器的Guard检查以IllegalArgumentException失败
     */
    @Test
    public void testParseRejectsNullPart() {
        assertEquals(3, ormModel.requireEntityModel("io.nop.app.SimsExtField").getPkColumns().size());

        char sep = OrmConstants.COMPOSITE_PK_SEPARATOR;
        String idText = "entityName" + sep + "null" + sep + "fieldName";

        NopException err = assertThrows(NopException.class,
                () -> OrmCompositePk.parse(ormModel.requireEntityModel("io.nop.app.SimsExtField"), idText));
        assertEquals(OrmErrors.ERR_ORM_INVALID_COMPOSITE_PK_PART.getErrorCode(), err.getErrorCode());

        // 正常解析仍然工作
        OrmCompositePk pk = OrmCompositePk.parse(ormModel.requireEntityModel("io.nop.app.SimsExtField"),
                "entityName" + sep + "entityId" + sep + "fieldName");
        assertEquals("entityName", pk.get(0));
    }
}
