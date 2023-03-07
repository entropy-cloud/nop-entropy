/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json;

import io.nop.core.lang.json.delta.JsonMerger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJsonMerger {
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
}
