package io.nop.graphql.core.utils;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下载文件名契约：中文文件名必须通过 RFC 5987 的 {@code filename*=UTF-8''<pct-encoded>} 表达，
 * 否则前端只能拿到未解码的百分号串，下载文件名显示为乱码。
 */
public class TestGraphQLResponseHelper {

    @Test
    public void testChineseFileNameEmitsRfc5987() {
        String fileName = "01-档案式报表.xlsx";
        WebContentBean bean = new WebContentBean("application/octet-stream", "x", fileName);

        Map<String, Object> headers = new HashMap<>();
        GraphQLResponseHelper.consumeWebContent(ApiResponse.success(bean), (headerSet, content, status) -> {
            headerSet.accept((name, value) -> headers.put(name, value));
            return null;
        });

        String cd = (String) headers.get("content-disposition");
        assertNotNull(cd, "content-disposition must be set for file download");

        String marker = "filename*=UTF-8''";
        int idx = cd.indexOf(marker);
        assertTrue(idx >= 0, "content-disposition must carry RFC5987 filename*: " + cd);

        String encoded = cd.substring(idx + marker.length());
        assertEquals(fileName, URLDecoder.decode(encoded, StandardCharsets.UTF_8),
                "filename* value must decode back to the original Chinese file name");
    }
}
