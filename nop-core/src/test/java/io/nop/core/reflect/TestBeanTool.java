/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.commons.collections.KeyedList;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBeanTool extends BaseTestCase {
    @Test
    public void testBeanCopier() {
        String str = "{a:1,b:[1,2,3],c:'3',map:{x:{a:'a1'}}}";
        JObject obj = (JObject) JsonTool.parseBeanFromText(str, JObject.class);

        MyClass my = BeanTool.castBeanToType(obj, MyClass.class);
        assertEquals("{\"a\":\"1\",\"b\":[\"1\",\"2\",\"3\"],\"c\":3,\"map\":{\"x\":{\"a\":\"a1\",\"c\":0}}}",
                JsonTool.stringify(my));
        assertEquals("a1", my.getMap().get("x").getA());

        my = BeanTool.buildBean(obj, MyClass.class);
        assertEquals("{\"a\":\"1\",\"b\":[\"1\",\"2\",\"3\"],\"c\":3,\"map\":{\"x\":{\"a\":\"a1\",\"c\":0}}}",
                JsonTool.stringify(my));

        MyClass other = new MyClass();
        BeanTool.copyBean(obj, other, MyClass.class, true);
        assertEquals("{\"a\":\"1\",\"b\":[\"1\",\"2\",\"3\"],\"c\":3,\"map\":{\"x\":{\"a\":\"a1\",\"c\":0}}}",
                JsonTool.stringify(other));
    }

    @DataBean
    public static class MyClass {
        String a;
        List<String> b;
        int c;
        Map<String, MyClass> map;

        public String getA() {
            return a;
        }

        public void setA(String a) {
            this.a = a;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<String> getB() {
            return b;
        }

        public void setB(List<String> b) {
            this.b = b;
        }

        public int getC() {
            return c;
        }

        public void setC(int c) {
            this.c = c;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Map<String, MyClass> getMap() {
            return map;
        }

        public void setMap(Map<String, MyClass> map) {
            this.map = map;
        }
    }

    @Test
    public void testQueryBean() {
        XNode node = attachmentXml("query.xml");
        node.clearLocation();
        QueryBean bean = BeanTool.buildBeanFromTreeBean(node, QueryBean.class);
        System.out.print(JsonTool.serialize(bean, true));
        assertEquals(attachmentJsonText("query.json"), JsonTool.serialize(bean, true));
    }

    @Test
    public void testBuildBean(){
        DynamicObject obj = new DynamicObject("obj");
        obj.addProp("name","a");
        KeyedList<DynamicObject> list = new KeyedList<>(DynamicObject::key);
        DynamicObject option = new DynamicObject("option","value");
        option.addProp("label","A");
        list.add(option);
        obj.addProp("options",list);

        DictBean bean = BeanTool.buildBean(obj, DictBean.class);
        assertEquals(1, bean.getOptions().size());
        assertTrue(bean.getOptions().get(0) instanceof DictOptionBean);
    }
}
