/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import com.fasterxml.jackson.annotation.JsonGetter;
import io.nop.api.core.annotations.data.DataBean;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJacksonAccessorAnnotations {

    @DataBean
    public static class AccessorBean {
        private String name;
        private int age;

        @JsonGetter("name")
        public String fetchName() {
            return name;
        }

        @JsonSetter("name")
        public void assignName(String name) {
            this.name = name;
        }

        @JsonProperty("years")
        public int fetchAge() {
            return age;
        }

        @JsonProperty("years")
        public void assignAge(int age) {
            this.age = age;
        }
    }

    @DataBean
    public static class GetterOnlyBean {
        private String name;

        @JsonGetter
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    public void testAnnotatedAccessors() {
        AccessorBean bean = new AccessorBean();
        bean.assignName("n");
        bean.assignAge(3);

        assertEquals("{\"name\":\"n\",\"years\":3}", JsonTool.stringify(bean));

        AccessorBean parsed = JsonTool.parseBeanFromText("{name:'n',years:3}", AccessorBean.class);
        assertEquals("n", parsed.fetchName());
        assertEquals(3, parsed.fetchAge());
    }

    @Test
    public void testJsonGetterWithoutName() {
        GetterOnlyBean bean = new GetterOnlyBean();
        bean.setName("n");
        assertEquals("{\"name\":\"n\"}", JsonTool.stringify(bean));

        GetterOnlyBean parsed = JsonTool.parseBeanFromText("{name:'n'}", GetterOnlyBean.class);
        assertEquals("n", parsed.getName());
    }
}
