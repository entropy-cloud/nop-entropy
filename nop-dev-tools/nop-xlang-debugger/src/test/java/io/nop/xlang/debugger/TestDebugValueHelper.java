package io.nop.xlang.debugger;

import io.nop.api.debugger.DebugValueKey;
import io.nop.api.debugger.DebugVariable;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归：数组展开此前 Array.getLength(index) 传错参数必抛 IllegalArgumentException；
 * Map子项展开此前index不自增且返回key而非value
 */
public class TestDebugValueHelper {

    static DebugValueKey key(int index) {
        DebugValueKey k = new DebugValueKey();
        k.setIndex(index);
        return k;
    }

    @Test
    public void testArrayExpandByIndex() {
        String[] arr = {"a", "b", "c"};
        // 修复前 Array.getLength(index) 抛 IllegalArgumentException: Argument is not an array
        List<DebugVariable> vars = DebugValueHelper.getExpandValue(arr,
                Collections.singletonList(key(1)));
        // index=1的元素是字符串"b"，简单类型不再展开，返回空列表但不应抛异常
        assertEquals(0, vars.size());

        // 展开数组的二维结构
        List<DebugVariable> all = DebugValueHelper.getExpandValue(arr, null);
        assertEquals(3, all.size());
        assertEquals("b", all.get(1).getValue());
    }

    @Test
    public void testMapExpandByIndexReturnsValue() {
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put("k1", "stringValue");
        map.put("k2", new int[]{7, 8});

        // 非String值走index路径：index=1应返回第二个entry的value（int数组），
        // 修复前返回key对象"k2"或因index不自增返回null
        List<DebugVariable> vars = DebugValueHelper.getExpandValue(map,
                Collections.singletonList(key(1)));
        assertEquals(2, vars.size());
        assertEquals("[0]", vars.get(0).getName());
        assertEquals(7, Integer.parseInt(vars.get(0).getValue()));
    }

    @Test
    public void testMapExpandByName() {
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put("name", "x");

        DebugValueKey k = new DebugValueKey();
        k.setName("name");
        List<DebugVariable> vars = DebugValueHelper.getExpandValue(map, Collections.singletonList(k));
        assertEquals(0, vars.size());
    }
}
