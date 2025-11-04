package io.nop.core.dict;

import io.nop.api.core.beans.DictBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEnumDictLoader {
    enum MyEnum{
        B, A, C, A1;
    }

    @Test
    public void testOrder(){
        DictBean dict = EnumDictLoader.INSTANCE.loadDict(null, MyEnum.class.getName(), null);
        assertEquals("[B, A, C, A1]", dict.getValues().toString());
    }
}
