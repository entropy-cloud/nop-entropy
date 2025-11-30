package io.nop.xlang.dict;

import io.nop.api.core.beans.DictBean;
import io.nop.core.dict.DictProvider;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDictModelLoader extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }


    @Test
    public void testLoad() {
        DictBean dict = DictProvider.instance().requireDict("en", "test/my", null, null);
        dict.getLabelByValue(1).equals("Item1");
    }
    
    @Test
    public void testDelta() {
        DictBean dict = DictProvider.instance().requireDict("en", "core/enabled-locale", null, null);
        System.out.println(JsonTool.serialize(dict, true));
        assertEquals(3, dict.getOptions().size());
    }
}
