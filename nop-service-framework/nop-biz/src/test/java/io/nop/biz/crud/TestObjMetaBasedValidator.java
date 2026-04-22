package io.nop.biz.crud;

import io.nop.biz.BizConstants;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ObjRelationWriteMode;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestObjMetaBasedValidator {

    @Test
    public void testSchemaWriteModeOverridesRequestMode() {
        ObjPropMetaImpl linkProp = newRelationProp("child", null);
        linkProp.setWriteMode(ObjRelationWriteMode.LINK);

        ObjPropMetaImpl bizProp = newRelationProp("child", null);
        bizProp.setWriteMode(ObjRelationWriteMode.BIZ);

        TestValidator validator = new TestValidator(newParentMeta(linkProp), Set.of("id"));
        Map<String, Object> request = map(BizConstants.PROP_WRITE_MODE + "_child", "biz");
        assertEquals(ObjRelationWriteMode.LINK, validator.resolve(linkProp, request));

        validator = new TestValidator(newParentMeta(bizProp), Set.of("id"));
        request = map(BizConstants.PROP_WRITE_MODE + "_child", "inline");
        assertEquals(ObjRelationWriteMode.BIZ, validator.resolve(bizProp, request));
    }

    @Test
    public void testRequestWriteModeOverridesInlineMode() {
        ObjPropMetaImpl prop = newRelationProp("child", null);
        prop.setWriteMode(ObjRelationWriteMode.INLINE);

        TestValidator validator = new TestValidator(newParentMeta(prop), Set.of("id"));
        Map<String, Object> request = map(BizConstants.PROP_WRITE_MODE + "_child", "link");

        assertEquals(ObjRelationWriteMode.LINK, validator.resolve(prop, request));
    }

    @Test
    public void testValidateForUpdateFiltersNestedPayloadInLinkMode() {
        ObjMetaImpl childMeta = newChildMeta();
        ObjPropMetaImpl prop = newRelationProp("child", childMeta);
        ObjMetaImpl parentMeta = newParentMeta(prop);

        TestValidator validator = new TestValidator(parentMeta, Set.of("id"));

        Map<String, Object> child = new LinkedHashMap<>();
        child.put("id", "1");
        child.put("name", "child-name");
        child.put("code", "child-code");
        child.put("_chgType", "update");

        Map<String, Object> request = new LinkedHashMap<>();
        request.put(BizConstants.PROP_WRITE_MODE + "_child", "link");
        request.put("child", child);

        Map<String, Object> validated = validator.validateForUpdate(request, null);
        Map<String, Object> validatedChild = (Map<String, Object>) validated.get("child");

        assertEquals("link", validated.get(BizConstants.PROP_WRITE_MODE + "_child"));
        assertEquals("1", validatedChild.get("id"));
        assertEquals("update", validatedChild.get("_chgType"));
        assertFalse(validatedChild.containsKey("name"));
        assertFalse(validatedChild.containsKey("code"));
    }

    @Test
    public void testValidateForUpdateKeepsNestedPayloadInBizMode() {
        ObjMetaImpl childMeta = newChildMeta();
        ObjPropMetaImpl prop = newRelationProp("child", childMeta);
        ObjMetaImpl parentMeta = newParentMeta(prop);

        TestValidator validator = new TestValidator(parentMeta, Set.of("id"));

        Map<String, Object> child = new LinkedHashMap<>();
        child.put("id", "1");
        child.put("name", "child-name");
        child.put("code", "child-code");

        Map<String, Object> request = new LinkedHashMap<>();
        request.put(BizConstants.PROP_WRITE_MODE + "_child", "biz");
        request.put("child", child);

        Map<String, Object> validated = validator.validateForUpdate(request, null);
        Map<String, Object> validatedChild = (Map<String, Object>) validated.get("child");

        assertEquals("1", validatedChild.get("id"));
        assertEquals("child-name", validatedChild.get("name"));
        assertEquals("child-code", validatedChild.get("code"));
        assertTrue(validated.containsKey(BizConstants.PROP_WRITE_MODE + "_child"));
    }

    private static ObjMetaImpl newParentMeta(ObjPropMetaImpl prop) {
        ObjMetaImpl meta = new ObjMetaImpl();
        meta.setBizObjName("Parent");
        meta.setProps(java.util.List.of(prop));
        return meta;
    }

    private static ObjMetaImpl newChildMeta() {
        ObjMetaImpl meta = new ObjMetaImpl();
        meta.setProps(java.util.List.of(newSimpleProp("name"), newSimpleProp("code")));
        return meta;
    }

    private static ObjPropMetaImpl newRelationProp(String name, ISchema schema) {
        ObjPropMetaImpl prop = new ObjPropMetaImpl();
        prop.setName(name);
        prop.setSchema(schema);
        prop.setUpdatable(true);
        prop.setInsertable(true);
        return prop;
    }

    private static ObjPropMetaImpl newSimpleProp(String name) {
        ObjPropMetaImpl prop = new ObjPropMetaImpl();
        prop.setName(name);
        prop.setUpdatable(true);
        prop.setInsertable(true);
        return prop;
    }

    private static Map<String, Object> map(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static class TestValidator extends ObjMetaBasedValidator {
        private final Set<String> identityProps;

        TestValidator(ObjMetaImpl objMeta, Set<String> identityProps) {
            super(null, objMeta.getBizObjName(), objMeta, null, false);
            this.identityProps = identityProps;
        }

        ObjRelationWriteMode resolve(IObjPropMeta propMeta, Map<String, Object> data) {
            return super.resolveWriteMode(propMeta, data);
        }

        @Override
        protected boolean isLinkIdentityProp(ISchema schema, String propName) {
            return identityProps.contains(propName);
        }
    }
}
