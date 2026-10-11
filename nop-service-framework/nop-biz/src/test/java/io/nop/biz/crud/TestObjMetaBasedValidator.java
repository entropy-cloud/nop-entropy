package io.nop.biz.crud;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.MultiCsvSet;
import io.nop.auth.api.AuthApiErrors;
import io.nop.biz.BizConstants;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ObjRelationWriteMode;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    public void testSimpleSchemaValidatorExecutedOnlyOnce() {
        ObjPropMetaImpl prop = newSimpleProp("code");
        SchemaImpl schema = new SchemaImpl();
        AtomicInteger count = new AtomicInteger();
        schema.setValidator((thisObj, args, scope) -> {
            count.incrementAndGet();
            return null;
        });
        prop.setSchema(schema);

        ObjMetaImpl parentMeta = newParentMeta(prop);
        TestValidator validator = new TestValidator(parentMeta, Set.of("id"));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("code", "c-1");

        validator.validateForSave(request, null);

        // 修复前：simple schema的validator在SimpleSchemaValidator内外各执行一次
        assertEquals(1, count.get());
    }

    // ===== P1-2.6 fail-open 同族收敛（plan 2378 Phase 3）：ObjMetaBasedValidator.doCheckAuth =====

    private Boolean oldRequire;

    @BeforeEach
    public void captureRequireConfig() {
        oldRequire = ApiConfigs.CFG_AUTH_REQUIRE_AUTH_CHECKER.get();
    }

    @AfterEach
    public void restoreRequireConfig() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_REQUIRE_AUTH_CHECKER, oldRequire);
    }

    /**
     * require-auth-checker=true 且字段权限已显式声明而 checker 缺席：fail-closed 抛
     * ERR_AUTH_NO_PERMISSION_FOR_FIELD（修复前静默 return = fail-open）。
     */
    @Test
    public void testRequireModeFailsClosedForDeclaredFieldAuthWithoutChecker() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_REQUIRE_AUTH_CHECKER, true);

        IServiceContext ctx = new ServiceContextImpl();
        AuthCheckValidator validator = new AuthCheckValidator(ctx, newParentMeta(newRelationProp("child", null)));
        ObjPropMetaImpl prop = newRelationProp("child", null);
        ActionAuthMeta auth = new ActionAuthMeta(false, CollectionHelper.buildImmutableSet("admin"),
                MultiCsvSet.fromText("Parent:mutation"), false);

        NopException e = assertThrows(NopException.class, () -> validator.checkAuth("Parent", prop, auth),
                "require mode must fail closed for declared field auth without checker");
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION_FOR_FIELD.getErrorCode(), e.getErrorCode());
    }

    /**
     * 默认配置（require=false）：既有 fail-open 语义保持——字段权限声明+无 checker 静默跳过。
     */
    @Test
    public void testDefaultModeKeepsSkippingFieldAuthWithoutChecker() {
        IServiceContext ctx = new ServiceContextImpl();
        AuthCheckValidator validator = new AuthCheckValidator(ctx, newParentMeta(newRelationProp("child", null)));
        ObjPropMetaImpl prop = newRelationProp("child", null);
        ActionAuthMeta auth = new ActionAuthMeta(false, CollectionHelper.buildImmutableSet("admin"),
                MultiCsvSet.fromText("Parent:mutation"), false);

        validator.checkAuth("Parent", prop, auth);
    }

    /**
     * auth==null 半支保持显式声明模型：无声明即跳过（strict 不动该半支——isAllowAccess 层处理）。
     */
    @Test
    public void testNullFieldAuthStillSkipped() {
        AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_AUTH_REQUIRE_AUTH_CHECKER, true);
        IServiceContext ctx = new ServiceContextImpl();
        AuthCheckValidator validator = new AuthCheckValidator(ctx, newParentMeta(newRelationProp("child", null)));
        ObjPropMetaImpl prop = newRelationProp("child", null);

        validator.checkAuth("Parent", prop, null);
    }

    private static class AuthCheckValidator extends ObjMetaBasedValidator {
        AuthCheckValidator(IServiceContext context, ObjMetaImpl objMeta) {
            super(null, objMeta.getBizObjName(), objMeta, context, false);
        }

        void checkAuth(String objTypeName, IObjPropMeta propMeta, ActionAuthMeta auth) {
            doCheckAuth(objTypeName, propMeta, auth);
        }
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
