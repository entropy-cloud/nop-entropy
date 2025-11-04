package io.nop.core.model.mapper;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestValueMapper {
    // Test parsing range mapper with valid pattern
    @Test
    void testParseRangeMapperValid() {
        Map<String, Object> config = new HashMap<>();
        config.put("(10,20]", "rangeValue");
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
        assertNotNull(mapper);
        assertEquals(1, mapper.getMappers().size());
        assertTrue(mapper.getMappers().get(0) instanceof NumberRangeValueMapper);
    }

    // Test parsing range mapper with invalid pattern
    @Test
    void testParseRangeMapperInvalid() {
        Map<String, Object> config = new HashMap<>();
        config.put("invalid", "rangeValue");
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
        assertNotNull(mapper);
        assertEquals(1, mapper.getMappers().size());
        assertFalse(mapper.getMappers().get(0) instanceof NumberRangeValueMapper);
    }

    // Test parsing regex mapper with valid pattern
    @Test
    void testParseRegexMapperValid() {
        Map<String, Object> config = new HashMap<>();
        config.put("/pattern/", "regexValue");
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
        assertNotNull(mapper);
        assertEquals(1, mapper.getMappers().size());
        assertTrue(mapper.getMappers().get(0) instanceof RegexValueMapper);
    }

    // Test parsing regex mapper with invalid pattern
    @Test
    void testParseRegexMapperInvalid() {
        Map<String, Object> config = new HashMap<>();
        config.put("invalid", "regexValue");
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
        assertNotNull(mapper);
        assertEquals(1, mapper.getMappers().size());
        assertFalse(mapper.getMappers().get(0) instanceof RegexValueMapper);
    }

    // Test parsing grouped mapper
    @Test
    void testParseGroupedMapper() {
        Map<String, Object> config = new HashMap<>();
        config.put("value1|value2|value3", "groupedValue");
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
        assertNotNull(mapper);
        assertEquals(1, mapper.getMappers().size());
        assertTrue(mapper.getMappers().get(0) instanceof GroupedValueMapper);
    }

    // Test parsing match all mapper
    @Test
    void testParseMatchAllMapper() {
        Map<String, Object> config = new HashMap<>();
        config.put("*", "matchAllValue");
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
        assertNotNull(mapper);
        assertEquals(1, mapper.getMappers().size());
        assertTrue(mapper.getMappers().get(0) instanceof MatchAllValueMapper);
    }

    // Test parsing exact match mapper
    @Test
    void testParseExactMatchMapper() {
        Map<String, Object> config = new HashMap<>();
        config.put("exactValue", "exactMatchValue");
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
        assertNotNull(mapper);
        assertEquals(1, mapper.getMappers().size());
        assertTrue(mapper.getMappers().get(0) instanceof ExactMatchValueMapper);
    }

    // Test parsing multiple mappers
    @Test
    void testParseMultipleMappers() {
        Map<String, Object> config = new HashMap<>();
        config.put("(10,20]", "rangeValue");
        config.put("/pattern/", "regexValue");
        config.put("value1|value2|value3", "groupedValue");
        config.put("*", "matchAllValue");
        config.put("exactValue", "exactMatchValue");
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
        assertNotNull(mapper);
        assertEquals(5, mapper.getMappers().size());
    }

    // Test parsing with null config
    @Test
    void testParseWithNullConfig() {
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(null);
        assertNull(mapper);
    }

    // Test parsing with empty config
    @Test
    void testParseWithEmptyConfig() {
        Map<String, Object> config = new HashMap<>();
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
        assertNull(mapper);
    }

    // Test parsing with invalid number in range mapper
    @Test
    void testParseRangeMapperInvalidNumber() {
        Map<String, Object> config = new HashMap<>();
        config.put("(10,invalid]", "rangeValue");
        try {
            CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
            fail();
        } catch (NopException e) {
            assertEquals(ApiErrors.ERR_CONVERT_TO_TYPE_FAIL.getErrorCode(), e.getErrorCode());
        }
    }

    // Test parsing with invalid format in range mapper
    @Test
    void testParseRangeMapperInvalidFormat() {
        Map<String, Object> config = new HashMap<>();
        config.put("10,20]", "rangeValue");
        CompositeValueMapper<String, Object> mapper = ValueMapperParser.INSTANCE.parseMapper(config);
        assertNotNull(mapper);
        assertEquals(1, mapper.getMappers().size());
        assertFalse(mapper.getMappers().get(0) instanceof NumberRangeValueMapper);
    }
}
