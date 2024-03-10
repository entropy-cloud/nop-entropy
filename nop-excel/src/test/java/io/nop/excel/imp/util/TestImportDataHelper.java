/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp.util;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestImportDataHelper extends BaseTestCase {
    @Test
    public void testNormalizeTree() {
        List<Object> list = new ArrayList<>();
        list.add(newRecord("A1", 1));
        list.add(newRecord("A1-1", 2));
        list.add(newRecord("A1-2", 2));
        list.add(newRecord("A2", 1));
        list.add(newRecord("A2-1", 2));
        list.add(newRecord("A2-1-1", 3));
        list.add(newRecord("A2-2", 2));

        ImportDataHelper.normalizeTree(list, "children", "level", null);
        assertEquals(this.attachmentJsonText("result.json"), JsonTool.serialize(list,true));

    }

    private Map<String, Object> newRecord(String name, int level) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("level", level);
        return map;
    }
}
