package io.nop.dyn.dao.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.dyn.dao.entity.NopDynEntityRelationMeta;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.Test;

import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_MIDDLE_ENTITY_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 同名中间表最多承载两个m2m关系（左右表各一），第三个关系必须报错而不是静默覆盖第二个关系
 */
public class TestMiddleEntityInfo {

    static DynEntityMetaToOrmModel.MiddleEntityInfo newInfo() {
        return new DynEntityMetaToOrmModel.MiddleEntityInfo();
    }

    static OrmEntityModel newEntity(String name) {
        OrmEntityModel model = new OrmEntityModel();
        model.setName(name);
        return model;
    }

    static NopDynEntityRelationMeta newRelation(String name) {
        NopDynEntityRelationMeta rel = new NopDynEntityRelationMeta();
        rel.setRelationName(name);
        return rel;
    }

    @Test
    public void testTwoRelationsAllowed() {
        DynEntityMetaToOrmModel.MiddleEntityInfo info = newInfo();
        info.addRelation(newEntity("app.User"), newRelation("userRoles"));
        info.addRelation(newEntity("app.Role"), newRelation("roleUsers"));
        assertEquals("app.User", info.getEntityNameA());
        assertEquals("app.Role", info.getEntityNameB());
    }

    @Test
    public void testThirdRelationRejected() {
        DynEntityMetaToOrmModel.MiddleEntityInfo info = newInfo();
        info.addRelation(newEntity("app.User"), newRelation("userRoles"));
        info.addRelation(newEntity("app.Role"), newRelation("roleUsers"));

        NopException ex = assertThrows(NopException.class,
                () -> info.addRelation(newEntity("app.Group"), newRelation("userGroups")));
        assertEquals(ERR_DYN_MIDDLE_ENTITY_CONFLICT.getErrorCode(), ex.getErrorCode());
    }
}
