package io.nop.sys.service;

import io.nop.orm.support.IOrmCompactExtFieldSupport;
import io.nop.sys.service.impl.SysCompactExtFieldHelper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * check2 审计 [P3]：getExtValues 对未配置的泛化 extN 槽位返回原始空格 " "，
 * 与已配置字段"空格回落默认值（未配置时为 null）"的空值语义不一致。
 */
public class TestSysCompactExtFieldHelper {

    @Test
    public void testUnconfiguredBlankSlotReturnsNull() {
        SysCompactExtFieldHelper helper = new SysCompactExtFieldHelper();
        FakeCompactExtFieldEntity entity = new FakeCompactExtFieldEntity(" A");

        Map<String, String> values = helper.getExtValues(entity);

        assertEquals("A", values.get("ext2"));
        assertNull(values.get("ext1"),
                "blank unconfigured slot must use null instead of ' ' to keep empty-value semantics uniform");
    }

    static class FakeCompactExtFieldEntity implements IOrmCompactExtFieldSupport {
        private final String extFlags;

        FakeCompactExtFieldEntity(String extFlags) {
            this.extFlags = extFlags;
        }

        @Override
        public String orm_entityName() {
            return "fake-compact-ext-entity";
        }

        @Override
        public String getExtFlags() {
            return extFlags;
        }

        @Override
        public void setExtFlags(String flags) {
        }
    }
}
