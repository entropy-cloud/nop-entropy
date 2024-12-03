/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen;

import io.nop.api.core.json.JSON;
import io.nop.batch.gen.generator.BatchGenContextImpl;
import io.nop.batch.gen.generator.BatchGenState;
import io.nop.batch.gen.model.BatchGenModel;
import io.nop.batch.gen.model.BatchGenModelParser;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBatchGenModel extends BaseTestCase {
    @Test
    public void testGen() {
        IResource resource = attachmentResource("test.batch-gen.json");
        BatchGenModel genModel = new BatchGenModelParser().parseFromResource(resource);

        JSON.registerProvider(JsonTool.instance());

        List<Map<String, Object>> list = genAll(genModel, 5);
        System.out.println(JSON.serialize(list, true));

        assertEquals(5, list.size());
        assertEquals(1, list.get(0).get("a"));
        assertEquals(3, list.get(0).get("c"));
        assertEquals(3, list.get(1).get("c"));
        assertEquals(4, list.get(2).get("c"));
        assertEquals(4, list.get(3).get("c"));
        assertEquals(4, list.get(4).get("c"));
    }

    <T> List<T> genAll(BatchGenModel genModel, int totalCount) {
        BatchGenState genState = new BatchGenState(genModel, totalCount);
        BatchGenContextImpl context = new BatchGenContextImpl(ValueResolverCompilerRegistry.DEFAULT);

        List<Object> ret = new ArrayList<>();
        while (genState.hasNext()) {
            Object item = genState.next(context, true);
            ret.add(item);
        }
        return (List) ret;
    }
}
