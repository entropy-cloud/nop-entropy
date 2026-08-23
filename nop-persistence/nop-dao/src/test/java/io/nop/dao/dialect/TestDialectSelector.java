/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * selector排序约定：针对具体版本的selector排在通配（null字段）selector之前，
 * 否则DialectManager按顺序取第一个match时，版本特化selector永远无法被选中
 */
public class TestDialectSelector {

    private static DialectSelector selector(String dialectName, String productVersion, String driverName) {
        DialectSelector s = new DialectSelector();
        s.setDialectName(dialectName);
        s.setProductName("MySQL");
        s.setProductVersion(productVersion);
        s.setDriverName(driverName);
        return s;
    }

    @Test
    public void testWildcardSortsAfterVersionedSelector() {
        DialectSelector wildcard = selector("mysql", null, null);
        DialectSelector versioned = selector("mysql5.7", "5.7", null);
        DialectSelector driverSpecific = selector("mysql-driver", null, "MySQL Connector/J");

        List<DialectSelector> list = new ArrayList<>();
        list.add(wildcard);
        list.add(driverSpecific);
        list.add(versioned);
        Collections.sort(list);

        // 通配排在最后，具体版本排在前面
        assertEquals("mysql", list.get(2).getDialectName(), "wildcard selector must sort last");
        assertTrue(list.get(0).compareTo(wildcard) < 0);
        assertTrue(list.get(1).compareTo(wildcard) < 0);
    }

    @Test
    public void testIntWildcardStillSortsLast() {
        DialectSelector s1 = selector("a", null, null);
        DialectSelector s2 = selector("a", null, null);
        s1.setDriverMajorVersion(8);
        s2.setDriverMajorVersion(0);

        List<DialectSelector> list = new ArrayList<>();
        list.add(s2);
        list.add(s1);
        Collections.sort(list);

        assertEquals(8, list.get(0).getDriverMajorVersion());
        assertEquals(0, list.get(1).getDriverMajorVersion());
    }

    @Test
    public void testVersionedSelectorMatchesBeforeWildcard() {
        // 模拟DialectManager的选择逻辑：排序后顺序遍历取第一个match
        DialectSelector wildcard = selector("mysql", null, null);
        DialectSelector versioned = selector("mysql5.7", "5.7.38", null);

        List<DialectSelector> list = new ArrayList<>();
        list.add(wildcard);
        list.add(versioned);
        Collections.sort(list);

        String matched = null;
        for (DialectSelector s : list) {
            if (s.match("MySQL", "5.7.38", null, 0, 0)) {
                matched = s.getDialectName();
                break;
            }
        }
        assertEquals("mysql5.7", matched, "version-specific dialect must win over wildcard");
    }
}
