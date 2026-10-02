package io.nop.sys.dao.dict;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysDict;
import io.nop.sys.dao.entity.NopSysDictOption;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SysDictLoader 实现语义：sys/ 前缀识别、按 dict.dictName 加载选项并按 value 升序输出、
 * 标记位（deprecated/internal）字符串"1"转布尔、existsDict 按实体表存在性判断。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysDictLoader extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private SysDictLoader newLoader() {
        SysDictLoader loader = new SysDictLoader();
        loader.daoProvider = daoProvider;
        return loader;
    }

    private NopSysDict saveDict(String sid, String dictName) {
        NopSysDict dict = new NopSysDict();
        dict.setSid(sid);
        dict.setDictName(dictName);
        dict.setDisplayName(dictName);
        daoProvider.daoFor(NopSysDict.class).saveEntity(dict);
        return dict;
    }

    private void saveOption(String sid, NopSysDict dict, String value, String label,
                            Byte deprecated, Byte internal) {
        NopSysDictOption option = new NopSysDictOption();
        option.setSid(sid);
        option.setDict(dict);
        option.setValue(value);
        option.setLabel(label);
        option.setIsDeprecated(deprecated);
        option.setIsInternal(internal);
        daoProvider.daoFor(NopSysDictOption.class).saveEntity(option);
    }

    @Test
    public void testSupportDictOnlyForSysPrefix() {
        SysDictLoader loader = newLoader();
        assertTrue(loader.supportDict("sys/" + "billing.status"),
                "sys/ 前缀的字典必须由 SysDictLoader 支持");
        assertFalse(loader.supportDict("app/other.dict"),
                "非 sys/ 前缀的字典不应由 SysDictLoader 支持");
        assertFalse(loader.supportDict(""), "空字典名不应被支持");
        assertEquals(NopSysDaoConstants.SYS_DICT_PREFIX, "sys/");
    }

    @Test
    public void testLoadDictMapsOptionsOrderedByValue() {
        NopSysDict dict = saveDict("dict-1", "wi7.test.dict");
        saveOption("opt-1", dict, "B", "label-B", (byte) 1, (byte) 0);
        saveOption("opt-2", dict, "A", "label-A", (byte) 0, (byte) 1);
        saveOption("opt-3", dict, "C", "label-C", null, null);

        SysDictLoader loader = newLoader();
        DictBean bean = loader.loadDict(null, "wi7.test.dict", null);

        assertNotNull(bean, "已存在选项的字典必须能加载出 DictBean");
        assertEquals("wi7.test.dict", bean.getName());

        List<DictOptionBean> options = bean.getOptions();
        assertEquals(3, options.size(), "必须加载出该字典下的全部选项");
        assertEquals("A", options.get(0).getValue(), "选项必须按 value 升序排列");
        assertEquals("B", options.get(1).getValue());
        assertEquals("C", options.get(2).getValue());

        assertEquals("label-A", options.get(0).getLabel());
        assertTrue(options.get(1).isDeprecated(), "isDeprecated=1 必须映射为 true");
        assertFalse(options.get(1).isInternal(), "isInternal=0 必须映射为 false");
        assertTrue(options.get(0).isInternal(), "isInternal=1 必须映射为 true");
        assertFalse(options.get(2).isDeprecated(), "isDeprecated 为 null 时必须映射为 false");
        assertFalse(options.get(2).isInternal());
    }

    @Test
    public void testLoadDictIgnoresOtherDictOptions() {
        NopSysDict dictA = saveDict("dict-a", "wi7.dict.a");
        saveDict("dict-b", "wi7.dict.b");
        saveOption("opt-a", dictA, "A1", "label-A1", (byte) 0, (byte) 0);

        SysDictLoader loader = newLoader();
        DictBean bean = loader.loadDict(null, "wi7.dict.b", null);
        assertNotNull(bean);
        assertTrue(bean.getOptions() == null || bean.getOptions().isEmpty(),
                "其他字典的选项不得混入当前字典的加载结果");
    }

    @Test
    public void testExistsDictByDictName() {
        saveDict("dict-exists", "wi7.exists.dict");

        SysDictLoader loader = newLoader();
        assertTrue(loader.existsDict("wi7.exists.dict"),
                "已保存的 dictName 必须判定为存在");
        assertFalse(loader.existsDict("wi7.missing.dict"),
                "不存在的 dictName 必须判定为不存在");
    }
}
