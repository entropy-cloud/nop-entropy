/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.init;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static io.nop.orm.model.OrmModelConstants.EXT_ENC_ASSISTED;
import static io.nop.orm.model.OrmModelConstants.EXT_ENC_LIKE;
import static io.nop.orm.model.OrmModelConstants.TAG_ENC;
import static io.nop.orm.model.OrmModelConstants.TAG_ENC_ASSISTED;
import static io.nop.orm.model.OrmModelConstants.TAG_ENC_LIKE;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_ASSISTED_NOT_ENC;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_CHAINED;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_DUPLICATE;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_NOT_VARCHAR;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_PRIMARY_KEY;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_TAG_MISMATCH;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_TAG_ORPHAN;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_ENC_HELPER_UNKNOWN_COLUMN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 2318 Phase 1：可检索加密声明（ext:enc-assisted / ext:enc-like + 角色标签）的模型加载期校验。
 */
public class TestEncAssistedModelValidation {

    static OrmColumnModel column(String name, String code, int propId, StdSqlType sqlType, boolean primary,
                                 String... tags) {
        OrmColumnModel col = new OrmColumnModel();
        col.setName(name);
        col.setCode(code);
        col.setPropId(propId);
        col.setStdSqlType(sqlType);
        col.setPrimary(primary);
        if (tags.length > 0)
            col.setTagSet(new HashSet<>(Arrays.asList(tags)));
        return col;
    }

    static OrmEntityModel entity(String name, OrmColumnModel... cols) {
        OrmEntityModel entityModel = new OrmEntityModel();
        entityModel.setName(name);
        entityModel.setTableName("T_" + name.toUpperCase());
        entityModel.setColumns(new ArrayList<>(Arrays.asList(cols)));
        return entityModel;
    }

    static void init(OrmEntityModel... entities) {
        OrmModel model = new OrmModel();
        List<OrmEntityModel> list = new ArrayList<>();
        list.addAll(Arrays.asList(entities));
        model.setEntities(list);
        model.init();
    }

    static OrmColumnModel master(String name, int propId) {
        OrmColumnModel col = column(name, name.toUpperCase(), propId, StdSqlType.VARCHAR, false, TAG_ENC);
        col.setExtProp(EXT_ENC_ASSISTED, name + "Assisted");
        col.setExtProp(EXT_ENC_LIKE, name + "Like");
        return col;
    }

    static OrmColumnModel assisted(String name, int propId) {
        return column(name, name.toUpperCase(), propId, StdSqlType.VARCHAR, false, TAG_ENC_ASSISTED);
    }

    static OrmColumnModel like(String name, int propId) {
        return column(name, name.toUpperCase(), propId, StdSqlType.VARCHAR, false, TAG_ENC_LIKE);
    }

    @Test
    public void testValidDeclaration() {
        init(entity("A",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                master("email", 2),
                assisted("emailAssisted", 3),
                like("emailLike", 4)));
    }

    @Test
    public void testMasterNotTaggedEnc() {
        OrmColumnModel master = master("email", 2);
        master.setTagSet(new HashSet<>());

        NopException e = assertThrows(NopException.class, () -> init(entity("A",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                master,
                assisted("emailAssisted", 3),
                like("emailLike", 4))));
        assertEquals(ERR_ORM_ENC_ASSISTED_NOT_ENC.getErrorCode(), e.getErrorCode());
        assertEquals("email", e.getParam("propName"));
    }

    @Test
    public void testHelperColumnNotFound() {
        OrmColumnModel master = master("email", 2);
        master.setExtProp(EXT_ENC_ASSISTED, "notExist");

        NopException e = assertThrows(NopException.class, () -> init(entity("A",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                master,
                like("emailLike", 3))));
        assertEquals(ERR_ORM_ENC_HELPER_UNKNOWN_COLUMN.getErrorCode(), e.getErrorCode());
        assertEquals("notExist", e.getParam("colName"));
    }

    @Test
    public void testHelperTagMismatch() {
        // ext:enc-assisted 指向的列却标记了 enc-like 角色标签
        OrmColumnModel master = master("email", 2);
        master.setExtProp(EXT_ENC_ASSISTED, "emailLike");

        NopException e = assertThrows(NopException.class, () -> init(entity("A",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                master,
                like("emailLike", 3))));
        assertEquals(ERR_ORM_ENC_HELPER_TAG_MISMATCH.getErrorCode(), e.getErrorCode());
        assertEquals(TAG_ENC_ASSISTED, e.getParam("tag"));
    }

    @Test
    public void testHelperPrimaryKeyRejected() {
        OrmColumnModel master = column("email", "EMAIL", 2, StdSqlType.VARCHAR, false, TAG_ENC);
        master.setExtProp(EXT_ENC_ASSISTED, "sid2");
        OrmColumnModel helper = column("sid2", "SID2", 3, StdSqlType.VARCHAR, true, TAG_ENC_ASSISTED);

        NopException e = assertThrows(NopException.class, () -> init(entity("A",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                master, helper)));
        assertEquals(ERR_ORM_ENC_HELPER_PRIMARY_KEY.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testHelperNotVarchar() {
        OrmColumnModel master = master("email", 2);
        OrmColumnModel helper = column("emailAssisted", "EMAIL_ASSISTED", 3, StdSqlType.INTEGER, false, TAG_ENC_ASSISTED);

        NopException e = assertThrows(NopException.class, () -> init(entity("A",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                master, helper,
                like("emailLike", 4))));
        assertEquals(ERR_ORM_ENC_HELPER_NOT_VARCHAR.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testHelperChained() {
        OrmColumnModel master = master("email", 2);
        OrmColumnModel helper = assisted("emailAssisted", 3);
        helper.setExtProp(EXT_ENC_ASSISTED, "emailAssisted2");
        OrmColumnModel helper2 = assisted("emailAssisted2", 4);

        NopException e = assertThrows(NopException.class, () -> init(entity("A",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                master, helper, helper2,
                like("emailLike", 5))));
        assertEquals(ERR_ORM_ENC_HELPER_CHAINED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testSelfReferenceRejected() {
        OrmColumnModel master = master("email", 2);
        master.setExtProp(EXT_ENC_ASSISTED, "email");

        NopException e = assertThrows(NopException.class, () -> init(entity("A",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                master)));
        assertEquals(ERR_ORM_ENC_HELPER_CHAINED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testHelperTagOrphan() {
        NopException e = assertThrows(NopException.class, () -> init(entity("A",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("email", "EMAIL", 2, StdSqlType.VARCHAR, false, TAG_ENC),
                assisted("orphanAssisted", 3))));
        assertEquals(ERR_ORM_ENC_HELPER_TAG_ORPHAN.getErrorCode(), e.getErrorCode());
        assertEquals(TAG_ENC_ASSISTED, e.getParam("tag"));
    }

    @Test
    public void testDuplicateReference() {
        OrmColumnModel master1 = column("email1", "EMAIL1", 2, StdSqlType.VARCHAR, false, TAG_ENC);
        master1.setExtProp(EXT_ENC_ASSISTED, "shared");
        OrmColumnModel master2 = column("email2", "EMAIL2", 3, StdSqlType.VARCHAR, false, TAG_ENC);
        master2.setExtProp(EXT_ENC_LIKE, "shared");
        // shared 列同时带两个角色标签、被两个主列引用：校验按引用顺序命中 duplicate
        OrmColumnModel shared = column("shared", "SHARED", 4, StdSqlType.VARCHAR, false,
                TAG_ENC_ASSISTED, TAG_ENC_LIKE);

        NopException e = assertThrows(NopException.class, () -> init(entity("A",
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                master1, master2, shared)));
        assertEquals(ERR_ORM_ENC_HELPER_DUPLICATE.getErrorCode(), e.getErrorCode());
    }
}
