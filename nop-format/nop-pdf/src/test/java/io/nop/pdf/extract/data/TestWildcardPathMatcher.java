package io.nop.pdf.extract.data;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通配符路径匹配语义：精确段/一级通配* / 多级通配** / *BEFORE-FIRST* 锚定；
 * 返回值为 pattern 匹配段在 path 中的结束下标，-1 表示不匹配
 */
public class TestWildcardPathMatcher {

    private final WildcardPathMatcher matcher = new WildcardPathMatcher();

    @Test
    public void testExactMatchReturnsEndIndex() {
        List<String> pattern = Arrays.asList("项目", "合计");
        List<String> path = Arrays.asList("项目", "收入", "合计");
        // pattern 两段分别命中 path[0] 与 path[2]，结束下标为2
        assertEquals(2, matcher.match(pattern, path));
    }

    @Test
    public void testSingleLevelWildcardMatchesExactlyOneLevel() {
        List<String> pattern = Arrays.asList("a", "*", "c");
        assertEquals(2, matcher.match(pattern, Arrays.asList("a", "b", "c")));
        // * 只占位一级，但其后的字面量段允许向后跳级搜索：
        // "a"/"b"/"x"/"c" 中 * 消费 "b"，"c" 从 pos=2 起向后找到 path[3]
        assertEquals(3, matcher.match(pattern, Arrays.asList("a", "b", "x", "c")));
    }

    @Test
    public void testAnyLevelsWildcardSkipsZeroOrMoreLevels() {
        List<String> pattern = Arrays.asList("**", "c");
        assertEquals(2, matcher.match(pattern, Arrays.asList("a", "b", "c")));
        // ** 允许跳过0级
        assertEquals(0, matcher.match(pattern, Arrays.asList("c")));
    }

    @Test
    public void testBeforeFirstAnchorsToPathStart() {
        List<String> pattern = Arrays.asList("*BEFORE-FIRST*", "收入");
        // BEFORE-FIRST 位于起点时命中首个字面量
        assertEquals(0, matcher.match(pattern, Arrays.asList("收入", "合计")));
        // BEFORE-FIRST 之前已有字面量消费（pos>0）时匹配失败
        assertEquals(-1, matcher.match(Arrays.asList("项目", "*BEFORE-FIRST*", "合计"),
                Arrays.asList("项目", "收入", "合计")));
    }

    @Test
    public void testAnyLevelsNotAllowedAsLastPathElement() {
        // path 最后一级为 ** 时视为非法
        assertEquals(-1, matcher.match(Arrays.asList("a"), Arrays.asList("a", "**")));
    }

    @Test
    public void testNoMatchReturnsMinusOne() {
        assertEquals(-1, matcher.match(Arrays.asList("x"), Arrays.asList("a", "b")));
        // varargs 版本同样生效
        assertEquals(1, matcher.match(Arrays.asList("b"), new String[]{"a", "b"}));
        assertEquals(-1, matcher.match(Arrays.asList("z"), new String[]{"a", "b"}));
    }
}
