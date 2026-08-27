package io.nop.metadata.service;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.metadata.dao.entity.NopMetaDictItem;
import io.nop.metadata.dao.model.OrmModelImporter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 P3-12（2026-08-23 审计）回归（子项 3）：OrmModelImporter.buildDictItem 将 null 序列化为
 * 字面量 "null" 字符串入库。
 *
 * <p>缺陷机制：{@code item.setItemValue(String.valueOf(option.getValue()))}——value=null 的字典项
 * 产出 itemValue="null"，占据 UK(metaDictId, itemValue) 且语义错误（后续匹配可能把字面量 "null"
 * 当合法字典值）；多个 null 值项还会在 "null" 上互相 UK 碰撞。
 *
 * <p>修复：value=null 的字典项跳过不导入（无值项不可匹配、不占据 UK）。
 *
 * <p>mutate-fail：回退为 String.valueOf 时 null 项产出 itemValue="null" → 断言失败。
 */
public class TestOrmModelImporterDictItemNullValue {

    /** value=null 的选项被跳过；正常选项（字符串/数值/空串）不受影响。 */
    @Test
    public void testNullValuedDictOptionsSkipped() {
        DictBean dict = new DictBean();
        dict.setName("status");
        dict.setOptions(Arrays.asList(
                option("A", null),
                option("B", "b1"),
                option("C", 1),
                option("D", "")));

        List<NopMetaDictItem> items = new OrmModelImporter().buildDictItems(dict);

        assertEquals(3, items.size(), "null-valued option must be skipped, got: "
                + items.stream().map(NopMetaDictItem::getItemValue).collect(java.util.stream.Collectors.toList()));
        assertFalse(items.stream().anyMatch(i -> "null".equals(i.getItemValue())),
                "literal 'null' string must never be persisted as itemValue");
        // 正常项不受影响（保留 String.valueOf 转换语义）
        assertTrue(items.stream().anyMatch(i -> "b1".equals(i.getItemValue())));
        assertTrue(items.stream().anyMatch(i -> "1".equals(i.getItemValue())));
        assertTrue(items.stream().anyMatch(i -> "".equals(i.getItemValue())),
                "explicit empty-string value is a real value and must be kept");
    }

    /** 全部选项 value=null：产出 0 条（不写任何 "null" 行）。 */
    @Test
    public void testAllNullOptionsProduceNoItems() {
        DictBean dict = new DictBean();
        dict.setName("allnull");
        dict.setOptions(Arrays.asList(option("A", null), option("B", null)));

        List<NopMetaDictItem> items = new OrmModelImporter().buildDictItems(dict);
        assertTrue(items.isEmpty(), "all-null options must produce zero items");
    }

    private static DictOptionBean option(String label, Object value) {
        DictOptionBean opt = new DictOptionBean();
        opt.setLabel(label);
        opt.setValue(value);
        return opt;
    }
}
