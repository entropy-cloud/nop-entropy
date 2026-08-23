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
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_JOIN_COLUMNS_NOT_MATCH_PK;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_MODEL_REF_ENTITY_NO_PROP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestOrmModelInitializerRefValidation {

    static OrmColumnModel column(String name, String code, int propId, StdSqlType sqlType, boolean primary) {
        OrmColumnModel col = new OrmColumnModel();
        col.setName(name);
        col.setCode(code);
        col.setPropId(propId);
        col.setStdSqlType(sqlType);
        col.setPrimary(primary);
        return col;
    }

    static OrmEntityModel entity(String name, String tableName, boolean noPrimaryKey, OrmColumnModel... cols) {
        OrmEntityModel entityModel = new OrmEntityModel();
        entityModel.setName(name);
        entityModel.setTableName(tableName);
        entityModel.setNoPrimaryKey(noPrimaryKey);
        List<OrmColumnModel> columns = new ArrayList<>();
        for (OrmColumnModel col : cols) {
            columns.add(col);
        }
        entityModel.setColumns(columns);
        return entityModel;
    }

    static OrmToOneReferenceModel toOneRef(String name, String refEntityName, String... leftRightProps) {
        OrmToOneReferenceModel ref = new OrmToOneReferenceModel();
        ref.setName(name);
        ref.setRefEntityName(refEntityName);
        List<OrmJoinOnModel> joins = new ArrayList<>();
        for (int i = 0; i + 1 < leftRightProps.length; i += 2) {
            OrmJoinOnModel join = new OrmJoinOnModel();
            join.setLeftProp(leftRightProps[i]);
            join.setRightProp(leftRightProps[i + 1]);
            joins.add(join);
        }
        ref.setJoin(joins);
        return ref;
    }

    @Test
    public void testToOneRefToNoPkEntityIdPropThrowsWithContext() {
        // 无主键只读实体，idProp 为 null
        OrmEntityModel entityA = entity("EntityA", "tbl_a", false,
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("refId", "REF_ID", 2, StdSqlType.VARCHAR, false));
        OrmEntityModel entityB = entity("EntityB", "tbl_b", true,
                column("data", "DATA", 1, StdSqlType.VARCHAR, false));

        entityA.setRelations(List.of(toOneRef("b", "EntityB", "refId", "id")));

        OrmModel model = new OrmModel();
        model.setEntities(List.of(entityA, entityB));

        // 修复前 getIdProp().isColumnModel() 直接 NPE，无任何模型上下文
        NopException e = assertThrows(NopException.class, model::init);
        assertEquals(ERR_ORM_MODEL_REF_ENTITY_NO_PROP.getErrorCode(), e.getErrorCode());
        assertEquals("id", e.getParam("propName"));
        assertEquals("EntityB", e.getParam("refEntityName"));
    }

    @Test
    public void testJoinColumnsNotMatchPkThrowsWithContext() {
        // 复合主键 [pk1, pk2]，但两个 join 条件都引用 pk1，pk2 未被引用
        OrmEntityModel entityA = entity("EntityA", "tbl_a", false,
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("refId1", "REF_ID1", 2, StdSqlType.VARCHAR, false),
                column("refId2", "REF_ID2", 3, StdSqlType.VARCHAR, false));
        OrmEntityModel entityB = entity("EntityB", "tbl_b", false,
                column("pk1", "PK1", 1, StdSqlType.VARCHAR, true),
                column("pk2", "PK2", 2, StdSqlType.VARCHAR, true));

        entityA.setRelations(List.of(toOneRef("b", "EntityB", "refId1", "pk1", "refId2", "pk1")));

        OrmModel model = new OrmModel();
        model.setEntities(List.of(entityA, entityB));

        // 修复前抛 bare IllegalStateException("invalid join prop")，无模型上下文
        NopException e = assertThrows(NopException.class, model::init);
        assertEquals(ERR_ORM_MODEL_JOIN_COLUMNS_NOT_MATCH_PK.getErrorCode(), e.getErrorCode());
        assertEquals("EntityA", e.getParam("entityName"));
        assertEquals("EntityB", e.getParam("refEntityName"));
        assertEquals("pk2", e.getParam("colName"));
    }

    @Test
    public void testNormalCompositeJoinStillReordered() {
        OrmEntityModel entityA = entity("EntityA", "tbl_a", false,
                column("sid", "SID", 1, StdSqlType.VARCHAR, true),
                column("refId2", "REF_ID2", 3, StdSqlType.VARCHAR, false),
                column("refId1", "REF_ID1", 2, StdSqlType.VARCHAR, false));
        OrmEntityModel entityB = entity("EntityB", "tbl_b", false,
                column("pk1", "PK1", 1, StdSqlType.VARCHAR, true),
                column("pk2", "PK2", 2, StdSqlType.VARCHAR, true));

        OrmToOneReferenceModel ref = toOneRef("b", "EntityB", "refId2", "pk2", "refId1", "pk1");
        entityA.setRelations(List.of(ref));

        OrmModel model = new OrmModel();
        model.setEntities(List.of(entityA, entityB));
        model.init();

        // join 条件应按被引用实体主键顺序重排
        assertEquals("pk1", ref.getJoin().get(0).getRightProp());
        assertEquals("pk2", ref.getJoin().get(1).getRightProp());
    }
}
