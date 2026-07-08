package io.nop.xlang.xmeta;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.validate.ListValidationErrorCollector;
import io.nop.core.initialize.CoreInitialization;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import io.nop.xlang.xmeta.validate.SchemaBasedValidator;
import io.nop.xlang.xmeta.validate.ValidationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_SUB_TYPE_PROP;
import static io.nop.xlang.XLangErrors.ARG_SUB_TYPE_VALUE;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_MANDATORY_PROP_IS_EMPTY;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_UNION_NO_SUB_SCHEMA_DEFINITION;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_UNION_SUB_TYPE_PROP_IS_EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSchemaBasedValidator {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testUnionSchemaRoutesToMatchedSubSchema() {
        SchemaImpl unionSchema = newUnionSchema(withMandatoryProp("and", "left"), withMandatoryProp("or", "right"));
        ValidationResult result = validate(unionSchema, mapOf("$type", "and"));

        assertEquals(1, result.errorCollector.getErrors().size());
        assertEquals(ERR_SCHEMA_MANDATORY_PROP_IS_EMPTY.getErrorCode(), result.firstError().getErrorCode());
        assertEquals("left", result.firstError().getParam(ARG_PROP_NAME));
    }

    @Test
    public void testUnionSchemaFallsBackToWildcardSchema() {
        SchemaImpl unionSchema = newUnionSchema(withMandatoryProp("known", "left"), withMandatoryProp("*", "fallback"));
        ValidationResult result = validate(unionSchema, mapOf("$type", "custom"));

        assertEquals(1, result.errorCollector.getErrors().size());
        assertEquals(ERR_SCHEMA_MANDATORY_PROP_IS_EMPTY.getErrorCode(), result.firstError().getErrorCode());
        assertEquals("fallback", result.firstError().getParam(ARG_PROP_NAME));
    }

    @Test
    public void testUnionSchemaReportsMissingSubtype() {
        SchemaImpl unionSchema = newUnionSchema(withMandatoryProp("and", "left"));
        ValidationResult result = validate(unionSchema, new LinkedHashMap<>());

        assertEquals(1, result.errorCollector.getErrors().size());
        assertEquals(ERR_SCHEMA_UNION_SUB_TYPE_PROP_IS_EMPTY.getErrorCode(), result.firstError().getErrorCode());
        assertEquals("$type", result.firstError().getParam(ARG_SUB_TYPE_PROP));
    }

    @Test
    public void testUnionSchemaReportsUnknownSubtypeWithoutFallback() {
        SchemaImpl unionSchema = newUnionSchema(withMandatoryProp("and", "left"));
        ValidationResult result = validate(unionSchema, mapOf("$type", "missing"));

        assertEquals(1, result.errorCollector.getErrors().size());
        assertEquals(ERR_SCHEMA_UNION_NO_SUB_SCHEMA_DEFINITION.getErrorCode(), result.firstError().getErrorCode());
        assertEquals("$type", result.firstError().getParam(ARG_SUB_TYPE_PROP));
        assertEquals("missing", result.firstError().getParam(ARG_SUB_TYPE_VALUE));
    }

    private static ValidationResult validate(SchemaImpl schema, Map<String, Object> value) {
        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        ValidationContext ctx = new ValidationContext();
        ctx.setErrorCollector(collector);
        SchemaBasedValidator.instance().validate(schema, "TestBizObj", "payload", value, null, ctx);
        return new ValidationResult(collector);
    }

    private static SchemaImpl newUnionSchema(SchemaImpl... subSchemas) {
        SchemaImpl schema = new SchemaImpl();
        schema.setSubTypeProp("$type");
        schema.setOneOf(List.of(subSchemas));
        return schema;
    }

    private static SchemaImpl withMandatoryProp(String typeValue, String mandatoryPropName) {
        SchemaImpl schema = new SchemaImpl();
        schema.setTypeValue(typeValue);
        schema.setProps(List.of(newMandatoryProp("$type"), newMandatoryProp(mandatoryPropName)));
        return schema;
    }

    private static ObjPropMetaImpl newMandatoryProp(String name) {
        ObjPropMetaImpl prop = new ObjPropMetaImpl();
        prop.setName(name);
        prop.setMandatory(true);
        return prop;
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static class ValidationResult {
        private final ListValidationErrorCollector errorCollector;

        private ValidationResult(ListValidationErrorCollector errorCollector) {
            this.errorCollector = errorCollector;
        }

        private ErrorBean firstError() {
            assertTrue(!errorCollector.getErrors().isEmpty());
            return errorCollector.getErrors().get(0);
        }
    }
}
