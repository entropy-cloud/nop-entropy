/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.record.csv;

import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCsvHelper {
    @Test
    public void testParse() {
        IResource resource = new ClassPathResource("classpath:csv/test.csv");
        List<Map<String, Object>> list = CsvHelper.readCsv(resource);
        assertEquals(1, list.size());
        assertEquals("auto_test1", list.get(0).get("USER_NAME"));
    }
}
