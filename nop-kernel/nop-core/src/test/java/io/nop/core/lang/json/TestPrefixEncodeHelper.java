package io.nop.core.lang.json;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.lang.IClassLoader;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPrefixEncodeHelper {

    @Test
    public void testEncodeEmptyAndPrimitiveValues() {
        assertEquals("", PrefixEncodeHelper.encode(null));
        assertEquals("", PrefixEncodeHelper.encode(""));
        assertEquals("123", PrefixEncodeHelper.encode(123), "number should encode as its string form");
        assertEquals("1", PrefixEncodeHelper.encode(true));
        assertEquals("0", PrefixEncodeHelper.encode(false));
    }

    @Test
    public void testEncodeString() {
        assertEquals("plain", PrefixEncodeHelper.encode("plain"));
        // 首字母为 $ 的字符串加前缀转义，防止与控制前缀混淆
        assertEquals("$:$money", PrefixEncodeHelper.encode("$money"));
    }

    @Test
    public void testEncodeApiRequestAndResponse() {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("k", "v");
        request.setData(data);

        String encoded = PrefixEncodeHelper.encode(request);
        assertTrue(encoded.startsWith("$r:java.util.HashMap\n"),
                "ApiRequest should use $r: prefix + data class: " + encoded);
        assertTrue(encoded.contains("\"k\""), "json body should be included");

        ApiResponse<Map<String, Object>> response = new ApiResponse<>();
        response.setData(data);
        String encodedResp = PrefixEncodeHelper.encode(response);
        assertTrue(encodedResp.startsWith("$s:java.util.HashMap\n"),
                "ApiResponse should use $s: prefix: " + encodedResp);
    }

    @Test
    public void testEncodeOtherBeanUsesDataPrefix() {
        String encoded = PrefixEncodeHelper.encode(12345L);
        assertEquals("12345", encoded);

        Map<String, Object> bean = new HashMap<>();
        bean.put("a", 1);
        String encodedBean = PrefixEncodeHelper.encode(bean);
        assertTrue(encodedBean.startsWith("$d:java.util.HashMap\n"),
                "plain object should use $d: prefix: " + encodedBean);
    }

    @Test
    public void testDecodePlainString() {
        // 不以 $ 开头按原样返回
        assertEquals("plain", PrefixEncodeHelper.decode("plain", null));
        assertNull(PrefixEncodeHelper.decode(null, null));
        assertNull(PrefixEncodeHelper.decode("", null));
        // $: 前缀解码回原字符串
        assertEquals("$money", PrefixEncodeHelper.decode("$:$money", null));
        assertEquals("abc", PrefixEncodeHelper.decode("$:abc", null));
    }

    @Test
    public void testDecodeDataPrefixRoundTrip() {
        Map<String, Object> bean = new HashMap<>();
        bean.put("a", 1);
        String encoded = PrefixEncodeHelper.encode(bean);

        // $d: 携带具体类名时必须提供 IClassLoader
        Object decoded = PrefixEncodeHelper.decode(encoded, forNameLoader());
        assertTrue(decoded instanceof Map, "decoded $d: payload should be a Map");
        assertEquals(1, ((Map<?, ?>) decoded).get("a"));
    }

    private IClassLoader forNameLoader() {
        return name -> Class.forName(name);
    }

    @Test
    public void testDecodeRequestPrefixRoundTrip() {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("k", "v");
        request.setData(data);
        String encoded = PrefixEncodeHelper.encode(request);

        Object decoded = PrefixEncodeHelper.decode(encoded, forNameLoader());
        assertTrue(decoded instanceof ApiRequest, "decoded $r: payload should be ApiRequest");
        Object decodedData = ((ApiRequest<?>) decoded).getData();
        assertTrue(decodedData instanceof Map, "request data should deserialize as Map");
        assertEquals("v", ((Map<?, ?>) decodedData).get("k"));
    }

    @Test
    public void testDecodeInvalidPrefixThrows() {
        // $ 开头但不是已知前缀且无换行 -> 解码失败
        assertThrows(NopException.class, () -> PrefixEncodeHelper.decode("$unknown", null));
        // 有换行但前缀未知 -> 解码失败
        assertThrows(NopException.class, () -> PrefixEncodeHelper.decode("$x:java.util.Map\n{}", null));
    }

    @Test
    public void testLoadDataClassViaClassLoader() {
        // $d: 空类名视为 String
        Object decoded = PrefixEncodeHelper.decode("$d:\n\"abc\"", null);
        assertEquals("abc", decoded);

        IClassLoader loader = name -> {
            if ("java.util.HashMap".equals(name))
                return HashMap.class;
            throw new ClassNotFoundException(name);
        };
        assertEquals(HashMap.class, PrefixEncodeHelper.loadDataClass("java.util.HashMap", loader));
        assertThrows(NopException.class, () -> PrefixEncodeHelper.loadDataClass("no.such.Klass", loader));
    }
}
