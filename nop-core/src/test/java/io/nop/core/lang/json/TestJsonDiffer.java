/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json;

import io.nop.commons.lang.Undefined;
import io.nop.core.lang.json.delta.JsonCleaner;
import io.nop.core.lang.json.delta.JsonDiffer;
import io.nop.core.lang.json.delta.JsonMerger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJsonDiffer {
    @Test
    public void testDiff() {
        List<Object> aList = (List<Object>) JsonTool.parseNonStrict("['a',{a:1}]");
        Object ret = JsonDiffer.instance().diffList(aList, new ArrayList<>(aList));
        assertEquals(Undefined.undefined, ret);

        List<Object> bList = (List<Object>) JsonTool.parseNonStrict("[{a:1}]");
        ret = JsonDiffer.instance().diffList(aList, bList);
        assertEquals("[a, {a=1}]", ret.toString());

        List<Object> merged = (List<Object>) new JsonMerger().merge(new ArrayList<>(bList), ret);
        JsonCleaner.instance().cleanDelta(merged);
        assertEquals(aList, merged);
    }

    @Test
    public void testMergeWithKey() {
        List<Object> aList = (List<Object>) JsonTool.parseNonStrict("[{id:'1',v:'a'},{id:'2',v:'b'}]");

        Object ret = JsonDiffer.instance().diffList(aList, new ArrayList<>(aList));
        assertEquals(Undefined.undefined, ret);

        List<Object> bList = (List<Object>) JsonTool.parseNonStrict("[{a:1},{id:'1',v:'a'}]");
        ret = JsonDiffer.instance().diffList(bList, aList);
        assertEquals("[{a=1}, {id=1, x:virtual=true}, {id=2, x:override=remove}]", ret.toString());

        List<Object> merged = (List<Object>) new JsonMerger().merge(new ArrayList<>(aList), ret);
        JsonCleaner.instance().cleanDelta(merged);
        assertEquals(JsonTool.stringify(bList), JsonTool.stringify(merged));
    }

    @Test
    public void testDiffSimplify() {
        List<Object> aList = new ArrayList<>();
        List<Object> bList = new ArrayList<>();
        aList.add(newMap("1"));
        aList.add(newMap("2"));
        aList.add(newMap("3"));
        aList.add(newMap("x"));
        aList.add(newMap("4"));
        aList.add(newMap("5"));

        bList.add(newMap("1"));
        bList.add(newMap("2"));
        bList.add(newMap("3"));
        bList.add(newMap("4"));
        bList.add(newMap("5"));
        bList.add(newMap("y"));

        List<Object> diff = (List<Object>) JsonDiffer.instance().diffList(aList, bList);
        System.out.println(JsonTool.stringify(diff));
        assertEquals("[{id=3}, {id=x}, {id=5}, {id=y, x:override=remove}]", diff.toString());
    }

    @Test
    public void testDiffSimplify1() {
        List<Object> aList = new ArrayList<>();
        List<Object> bList = new ArrayList<>();
        aList.add(newMap("1"));
        aList.add(newMap("x"));
        aList.add(newMap("4"));
        aList.add(newMap("5"));
        aList.add(newMap("6"));
        aList.add(newMap("2"));

        bList.add(newMap("1"));
        bList.add(newMap("2"));
        bList.add(newMap("3"));
        bList.add(newMap("4"));
        bList.add(newMap("5"));
        bList.add(newMap("6"));
        bList.add(newMap("y"));
        bList.add(newMap("8"));

        List<Object> diff = (List<Object>) JsonDiffer.instance().diffList(aList, bList);
        assertEquals(
                "[{id=1}, {id=x}, {id=4}, {id=6}, {id=y, x:override=remove}, {id=8, x:override=remove}, {id=2}, {id=3, x:override=remove}]",
                diff.toString());

        List<Object> list = JsonMerger.instance().mergeList(bList, diff);
        JsonCleaner.instance().cleanDelta(list);
        assertEquals(aList.toString(), list.toString());
    }

    @Test
    public void testDiffSimplify2() {
        List<Object> aList = new ArrayList<>();
        List<Object> bList = new ArrayList<>();
        aList.add(newMap("1"));
        aList.add(newMap("2"));
        aList.add(newMap("x"));
        aList.add(newMap("3"));
        aList.add(newMap("4"));
        aList.add(newMap("y"));
        aList.add(newMap("6"));

        bList.add(newMap("1"));
        bList.add(newMap("2"));
        bList.add(newMap("3"));
        bList.add(newMap("4"));
        bList.add(newMap("5"));
        bList.add(newMap("6"));
        bList.add(newMap("y"));
        bList.add(newMap("8"));

        List<Object> diff = (List<Object>) JsonDiffer.instance().diffList(aList, bList);
        assertEquals("[{id=2}, {id=x}, {id=4}, {id=5, x:override=remove}, {id=y}, {id=8, x:override=remove}, {id=6}]",
                diff.toString());

        List<Object> list = JsonMerger.instance().mergeList(bList, diff);
        JsonCleaner.instance().cleanDelta(list);
        assertEquals(aList.toString(), list.toString());
    }

    Map<String, Object> newMap(String id) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        return map;
    }
}
