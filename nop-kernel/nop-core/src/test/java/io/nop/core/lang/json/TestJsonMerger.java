/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json;

import io.nop.core.lang.json.delta.JsonMerger;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestJsonMerger {
    @SuppressWarnings("unchecked")
    static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    /**
     * 如果标记了x:virtual，且在base中不存在，则节点自动
     */
    @Test
    public void testVirtual() {
        Object base = JsonTool.parseNonStrict("[{id:1,s:'a'},{id:2}]");
        Object ext = JsonTool.parseNonStrict("[{id:3},{id:4,'x:virtual':true},{id:1,v:3,'x:virtual':true}]");

        Object merged = JsonMerger.instance().merge(base, ext);
        System.out.println(JsonTool.serialize(merged, true));
        assertEquals("[{id=3}, {id=1, s=a, v=3}, {id=2}]", merged.toString());
    }

    /**
     * mergeMap不应修改入参mapB：自定义ResourceLoader可能返回缓存对象，
     * merge剥除x:virtual/x:inherit标记的副作用会污染缓存
     */
    @Test
    public void testMergeMapDoesNotMutateInput() {
        Map<String, Object> mapA = castMap(JsonTool.parseNonStrict("{a:1}"));
        Map<String, Object> mapB = castMap(JsonTool.parseNonStrict("{b:2,'x:virtual':true,'x:inherit':'/base.json'}"));

        Map<String, Object> merged = JsonMerger.instance().mergeMap(mapA, mapB);

        assertEquals("{a=1, b=2}", merged.toString());
        assertEquals(true, mapB.get("x:virtual"), "input mapB should keep x:virtual marker");
        assertEquals("/base.json", mapB.get("x:inherit"), "input mapB should keep x:inherit marker");
        assertNull(merged.get("x:virtual"));
        assertNull(merged.get("x:inherit"));
    }

    /**
     * 直接返回mapB引用的分支必须返回去除标记的副本，避免下游继续修改返回值时污染共享的mapB对象
     */
    @Test
    public void testMergeMapReturnsCopyInsteadOfInput() {
        Map<String, Object> mapA = new LinkedHashMap<>();
        Map<String, Object> mapB = castMap(JsonTool.parseNonStrict("{b:2,'x:virtual':true}"));

        Map<String, Object> merged = JsonMerger.instance().mergeMap(mapA, mapB);

        assertNotSame(mapB, merged);
        assertEquals("{b=2}", merged.toString());
        assertEquals(true, mapB.get("x:virtual"));
    }

    @Test
    public void testMergeMapNestedMergeContent() {
        Map<String, Object> mapA = castMap(JsonTool.parseNonStrict("{a:{x:1},list:[{id:1,v:1}]}"));
        Map<String, Object> mapB = castMap(JsonTool.parseNonStrict("{a:{y:2},list:[{id:1,w:2}],'x:inherit':'/base.json'}"));

        Map<String, Object> merged = JsonMerger.instance().mergeMap(mapA, mapB);

        assertEquals("{a={x=1, y=2}, list=[{id=1, v=1, w=2}]}", merged.toString());
        assertEquals("/base.json", mapB.get("x:inherit"), "input mapB should not be mutated");
    }
}
