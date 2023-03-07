/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.json.JSON;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJsonTool {

    @Test
    public void testParse() {
        JSON.registerProvider(JsonTool.instance());

        Object o = JSON.parseNonStrict(null, "{a:1}");
        String str = JSON.stringify(o);
        assertEquals("{\"a\":1}", str);
        assertEquals(o, JSON.parse(str));
    }

    @Test
    public void testJsonIgnore() {
        String text = "{$type:'eq',value:'ss'}";
        TreeBean bean = (TreeBean) JsonTool.parseBeanFromText(text, TreeBean.class);
        assertEquals("ss", bean.getAttr("value"));
    }

    @Test
    public void testSerialize() {
        JSON.registerProvider(JsonTool.instance());
        String str = JSON.stringify("<c:script>\r\na+b\n</c:script>");
        System.out.println(str);
        assertEquals("\"<c:script>\\r\\na+b\\n</c:script>\"", str);
        assertEquals("<c:script>\r\na+b\n</c:script>", JsonTool.parse(str));
    }

    @Test
    public void testTreeBean() {
        TreeBean bean = new TreeBean();
        bean.setTagName("a");
        bean.setAttr("b", 1);
        bean.setContentValue("x");
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(TreeBean.class);
        assertTrue(!beanModel.getPropertyModel("childCount").isSerializable());
        assertEquals("{\"$body\":\"x\",\"$type\":\"a\",\"b\":1}", JsonTool.stringify(bean));
    }

    @Test
    public void testGeneric() {
        ApiRequest<?> request = (ApiRequest<?>) JsonTool.parseBeanFromText("{\"data\":{}}", ApiRequest.class);
        assertTrue(request.getData() instanceof Map);
    }

    @Test
    public void testYaml() {
        JObject obj = new JObject();
        obj.put("a", null);
        obj.put("b", "null");
        String yaml = JsonTool.toYaml(obj);
        assertEquals("a: null\nb: 'null'\n", yaml);

        Map<String, Object> map = (Map<String, Object>) JsonTool.parseYaml(null, yaml);
        assertEquals("{\"a\":null,\"b\":\"null\"}", JsonTool.serialize(map, false));
    }
}
