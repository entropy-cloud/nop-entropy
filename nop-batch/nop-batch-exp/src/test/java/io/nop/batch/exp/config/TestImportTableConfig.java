package io.nop.batch.exp.config;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.batch.exp.DbToolExpErrors.ARG_FIELD_NAME;
import static io.nop.batch.exp.DbToolExpErrors.ARG_FIELD_NAMES;
import static io.nop.batch.exp.DbToolExpErrors.ARG_TABLE_NAME;
import static io.nop.batch.exp.DbToolExpErrors.ERR_EXP_UNKNOWN_KEY_FIELD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ImportTableConfig 语义：
 * 1. sourceTableName：from缺省回退到name（导入文件名按来源表命名）；
 * 2. target/source字段名列表必须跳过ignore字段，且source侧按from回退到字段名；
 * 3. keyFields必须能在fields中解析到，未知键字段必须显式报错并携带定位参数。
 */
public class TestImportTableConfig {

    private static TableFieldConfig field(String name, String from, boolean ignore) {
        TableFieldConfig field = new TableFieldConfig();
        field.setName(name);
        if (from != null)
            field.setFrom(from);
        field.setIgnore(ignore);
        return field;
    }

    private static ImportTableConfig newTableConfig() {
        ImportTableConfig config = new ImportTableConfig();
        config.setName("my_table");
        config.addField(field("id", null, false));
        config.addField(field("full_name", "name", false));
        config.addField(field("memo", null, true));
        config.setKeyFields(List.of("id", "full_name"));
        return config;
    }

    @Test
    public void testSourceTableNameFallsBackToName() {
        ImportTableConfig config = new ImportTableConfig();
        config.setName("my_table");
        assertEquals("my_table", config.getSourceTableName(), "from未设置时来源表名回退到name");

        config.setFrom("other_table");
        assertEquals("other_table", config.getSourceTableName(), "from设置后来源表名使用from");

        // ImportDbTool.newLoader会执行tableConfig.setFrom(getSourceTableName())，此后来源名即from
        config.setFrom(null);
        String resolved = config.getSourceTableName();
        config.setFrom(resolved);
        assertEquals("my_table", config.getSourceTableName());
    }

    @Test
    public void testTargetAndSourceFieldNamesSkipIgnoredFields() {
        ImportTableConfig config = newTableConfig();

        // ignore字段不参与导入：目标列与来源列列表一致地跳过memo
        assertEquals(List.of("id", "full_name"), config.getTargetFieldNames());
        assertEquals(List.of("id", "name"), config.getSourceFieldNames(),
                "source侧字段名按from回退，未设置from时回退到字段名自身");
    }

    @Test
    public void testKeyFieldConfigsResolveToDeclaredFields() {
        ImportTableConfig config = newTableConfig();

        List<TableFieldConfig> keyFields = config.getKeyFieldConfigs();
        assertEquals(2, keyFields.size());
        // 解析结果必须就是fields里声明的配置实例（携带stdDataType/transformExpr等定义）
        assertSame(config.getField("id"), keyFields.get(0));
        assertSame(config.getField("full_name"), keyFields.get(1));
    }

    @Test
    public void testUnknownKeyFieldThrowsWithLocatingParams() {
        ImportTableConfig config = newTableConfig();
        config.setKeyFields(List.of("id", "not_exist"));

        NopException ex = assertThrows(NopException.class, config::getKeyFieldConfigs);
        assertEquals(ERR_EXP_UNKNOWN_KEY_FIELD.getErrorCode(), ex.getErrorCode());
        assertEquals("not_exist", ex.getParam(ARG_FIELD_NAME));
        assertEquals("my_table", ex.getParam(ARG_TABLE_NAME));
        // 已定义字段名以集合形式携带（fields的keySet）
        java.util.Collection<?> declaredNames = (java.util.Collection<?>) ex.getParam(ARG_FIELD_NAMES);
        assertTrue(declaredNames.contains("full_name"), "error must list the declared field names");
    }

    @Test
    public void testKeyFieldConfigsEmptyWhenKeyFieldsUnset() {
        ImportTableConfig config = newTableConfig();
        config.setKeyFields(null);

        assertTrue(config.getKeyFieldConfigs().isEmpty(), "no keyFields configured -> no key field configs");
    }

    @Test
    public void testCloneInstanceDeepCopiesFields() {
        ImportTableConfig config = newTableConfig();
        config.setAllowUpdate(true);
        config.setMaxSkipCount(100);

        ImportTableConfig clone = config.cloneInstance();

        assertEquals("my_table", clone.getName());
        assertEquals(Boolean.TRUE, clone.getAllowUpdate());
        assertEquals(Integer.valueOf(100), clone.getMaxSkipCount());
        assertEquals(List.of("id", "full_name"), clone.getKeyFields());

        // fields必须深拷贝：修改克隆的字段配置不影响原配置
        assertNotSame(config.getFields(), clone.getFields());
        TableFieldConfig clonedField = clone.getField("id");
        assertNotSame(config.getField("id"), clonedField, "field configs must be deep-copied");
        clonedField.setIgnore(true);
        assertEquals(false, config.getField("id").isIgnore());
    }
}
