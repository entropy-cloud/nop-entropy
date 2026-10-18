/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.orm.IOrmEntity;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CrudBizModel} 只读开关：显式 setReadOnly 或 objMeta 含 {@code read-only} tag 时，
 * 全部写动作显式拒绝（通用错误码），读动作不受影响。
 */
public class TestReadOnlyCrudBizModel {

    static ObjMetaImpl objMetaWithTags(String... tags) {
        ObjMetaImpl meta = new ObjMetaImpl();
        meta.setTagSet(Set.of(tags));
        return meta;
    }

    @BizModel("ExplicitReadOnly")
    static class ExplicitReadOnlyModel extends CrudBizModel<IOrmEntity> {
        ExplicitReadOnlyModel() {
            setEntityName("test.ReadOnlyEntity");
            setReadOnly(true);
        }
    }

    @BizModel("MetaReadOnly")
    static class MetaReadOnlyModel extends CrudBizModel<IOrmEntity> {
        MetaReadOnlyModel() {
            setEntityName("test.ReadOnlyEntity");
        }

        @Override
        public IObjMeta getObjMeta() {
            return objMetaWithTags("log", "read-only");
        }
    }

    @BizModel("MetaWritable")
    static class MetaWritableModel extends CrudBizModel<IOrmEntity> {
        MetaWritableModel() {
            setEntityName("test.WritableEntity");
        }

        @Override
        public IObjMeta getObjMeta() {
            return objMetaWithTags("log");
        }
    }

    @BizModel("PlainWritable")
    static class PlainModel extends CrudBizModel<IOrmEntity> {
        PlainModel() {
            setEntityName("test.PlainEntity");
        }
    }

    private void assertReadOnly(CrudBizModel<?> model, String action, Runnable call) {
        NopException e = assertThrows(NopException.class, call::run);
        assertEquals("nop.err.biz.read-only-object", e.getErrorCode());
        assertEquals(action, e.getParam("actionName"));
    }

    private void assertAllMutationsRejected(CrudBizModel<?> model) {
        assertReadOnly(model, "save", () -> model.save(Map.of(), null));
        assertReadOnly(model, "update", () -> model.update(Map.of(), null));
        assertReadOnly(model, "delete", () -> model.delete("1", null));
        assertReadOnly(model, "batchUpdate", () -> model.batchUpdate(Set.of("1"), Map.of(), false, null));
        assertReadOnly(model, "batchDelete", () -> model.batchDelete(Set.of("1"), null));
        assertReadOnly(model, "batchModify", () -> model.batchModify(java.util.List.of(), null, null, null));
        assertReadOnly(model, "saveOrUpdate", () -> model.saveOrUpdate(Map.of(), null));
        assertReadOnly(model, "save_update", () -> model.save_update(Map.of(), null));
        assertReadOnly(model, "recoverDeleted", () -> model.recoverDeleted("1", null));
        assertReadOnly(model, "updateByQuery", () -> model.updateByQuery(null, Map.of(), null));
        assertReadOnly(model, "deleteByQuery", () -> model.deleteByQuery(null, null));
        assertReadOnly(model, "copyForNew", () -> model.copyForNew(Map.of(), null));
        assertReadOnly(model, "addManyToManyRelations",
                () -> model.addManyToManyRelations("1", "roles", java.util.List.of("2"), null, null));
        assertReadOnly(model, "removeManyToManyRelations",
                () -> model.removeManyToManyRelations("1", "roles", java.util.List.of("2"), null, null));
        assertReadOnly(model, "updateManyToManyRelations",
                () -> model.updateManyToManyRelations("1", "roles", java.util.List.of("2"), null, null));
    }

    @Test
    public void testExplicitReadOnlyRejectsAllMutations() {
        assertAllMutationsRejected(new ExplicitReadOnlyModel());
    }

    @Test
    public void testReadOnlyDerivedFromObjMetaTag() {
        MetaReadOnlyModel model = new MetaReadOnlyModel();
        assertTrue(model.isReadOnly(), "objMeta tag read-only must drive isReadOnly");
        assertAllMutationsRejected(model);
    }

    @Test
    public void testObjMetaWithoutTagIsWritable() {
        MetaWritableModel model = new MetaWritableModel();
        assertFalse(model.isReadOnly(), "objMeta without read-only tag must not be read-only");
    }

    @Test
    public void testExplicitFlagOverridesObjMeta() {
        MetaWritableModel writable = new MetaWritableModel();
        writable.setReadOnly(true);
        assertTrue(writable.isReadOnly());
    }

    @Test
    public void testNoContainerIsWritableAndEmptyDataStillFallsToBaseError() {
        PlainModel model = new PlainModel();
        assertFalse(model.isReadOnly(), "no bizObjectManager injected: objMeta is unresolvable, must not NPE");

        NopException e = assertThrows(NopException.class, () -> model.save(null, null));
        assertEquals("nop.err.biz.empty-data-for-save", e.getErrorCode());
    }
}
