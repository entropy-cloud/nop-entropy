/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.record.csv;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.FileResource;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
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

    @Test
    public void countDiff() {
        IResource resource = new FileResource(new File("c:/projects/data.csv"));
        List<Map<String, Object>> list = CsvHelper.readCsv(resource, null, CSVFormat.EXCEL.withDelimiter('\t'),"big5");

        List<Map<String, Object>> errors = new ArrayList<>();

        for (Map<String, Object> row : list) {
            String mobile = (String) row.get("mobile");
            if (!StringHelper.isAllDigit(mobile))
                continue;

            if("113".equals(row.get("data02")) &&  "4".equals(row.get("data03")))
                continue;

            int count = 0;
            if (containsId(row))
                count++;

            if (containsName(row))
                count++;

            if (count >= 2)
                errors.add(row);
        }

        System.out.println(JsonTool.serialize(errors,true));

        System.out.println(errors.size());

    }

    boolean containsName(Map<String, Object> row) {
        String data1 = (String) row.get("data01");
        String data2 = (String) row.get("data02");
        String data3 = (String) row.get("data03");
        String data4 = (String) row.get("data04");

        if (isName(data1) || isName(data2) || isName(data3) || isName(data4))
            return true;
        return false;
    }

    boolean isName(String str) {
        if (str == null)
            return false;

        if (str.length() >= 5)
            return false;
        if (str.length() < 2)
            return false;
        if (StringHelper.isNumber(str))
            return false;

        if (StringHelper.containsDigits(str))
            return false;

        return true;
    }

    boolean containsId(Map<String, Object> row) {
        String data1 = (String) row.get("data01");
        String data2 = (String) row.get("data02");
        String data3 = (String) row.get("data03");
        String data4 = (String) row.get("data04");

        if (isId(data1) || isId(data2) || isId(data3) || isId(data4))
            return true;
        return false;
    }

    public boolean isId(String str) {
        if (str == null)
            return false;
        if(StringHelper.isAllDigit(str) && (str.length() == 9 || str.length()==10))
            return true;
        if (str.length() != 11)
            return false;
        if(str.indexOf(':') >= 0 || str.indexOf('\t') >= 0 || str.indexOf(' ') >= 0)
            return false;
        return true;
    }
}
