/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestApiStringHelper {

    /**
     * 回归：encodeStringMap不产生尾部悬挂分隔符，与encodeQuery风格一致。
     */
    @Test
    public void testEncodeStringMapNoTrailingSeparator() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        assertEquals("a=1,b=2", ApiStringHelper.encodeStringMap(map));

        Map<String, Object> single = new LinkedHashMap<>();
        single.put("a", 1);
        assertEquals("a=1", ApiStringHelper.encodeStringMap(single));

        // null值只输出key=，与其他条目以分隔符连接
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("a", null);
        withNull.put("b", 2);
        assertEquals("a=,b=2", ApiStringHelper.encodeStringMap(withNull));

        assertEquals("", ApiStringHelper.encodeStringMap(new LinkedHashMap<>()));
        assertNull(ApiStringHelper.encodeStringMap(null));

        // 自定义分隔符
        Map<String, Object> svcRoute = new LinkedHashMap<>();
        svcRoute.put("test", "v1");
        assertEquals("test:v1", ApiStringHelper.encodeStringMap(svcRoute, ':', ','));
    }

    /**
     * 编码结果可被parseStringMap无损解析（往返一致）。
     */
    @Test
    public void testEncodeStringMapRoundTrip() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", "1");
        map.put("b", "2");
        Map<String, String> parsed = ApiStringHelper.parseStringMap(
                ApiStringHelper.encodeStringMap(map), '=', ',');
        assertEquals("1", parsed.get("a"));
        assertEquals("2", parsed.get("b"));
        assertEquals(2, parsed.size());
    }
}
