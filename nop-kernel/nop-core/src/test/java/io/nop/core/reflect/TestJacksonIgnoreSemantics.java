/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.BeanCopyOptions;
import io.nop.core.reflect.bean.BeanCopier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJacksonIgnoreSemantics {

    @DataBean
    public static class SimpleBean {
        private String name;
        private String secret;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonIgnore
        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    @DataBean
    public static class SetterIgnoredBean {
        private String name;
        private String secret;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSecret() {
            return secret;
        }

        @JsonIgnore
        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    @DataBean
    public static class PrivateFieldIgnoredBean {
        private String name;

        @JsonIgnore
        private String secret;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public abstract static class BaseBean {
        @JsonIgnore
        public abstract String getRole();
    }

    @DataBean
    public static class ChildBean extends BaseBean {
        private String name;
        private String role = "user";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    @DataBean
    public static class ValueFalseBean {
        private String name;
        private String visible;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonIgnore(false)
        public String getVisible() {
            return visible;
        }

        public void setVisible(String visible) {
            this.visible = visible;
        }
    }

    public static class AnySetterBean {
        private String name;
        private final Map<String, Object> extras = new LinkedHashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonIgnore
        public String getSecret() {
            return null;
        }

        public void setSecret(String secret) {
        }

        @JsonAnySetter
        public void putExtra(String name, Object value) {
            extras.put(name, value);
        }

        public Map<String, Object> getExtras() {
            return extras;
        }
    }

    @DataBean
    public static class AliasBean {
        private String name;

        @JsonAlias({"a", "b"})
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class AliasConflictBean {
        private String first;
        private String second;

        @JsonAlias("x")
        public String getFirst() {
            return first;
        }

        public void setFirst(String first) {
            this.first = first;
        }

        @JsonAlias("x")
        public String getSecond() {
            return second;
        }

        public void setSecond(String second) {
            this.second = second;
        }
    }

    @Test
    public void testJsonIgnoreWriteAndRead() {
        SimpleBean bean = new SimpleBean();
        bean.setName("n");
        bean.setSecret("s");

        assertEquals("{\"name\":\"n\"}", JsonTool.stringify(bean));

        SimpleBean parsed = JsonTool.parseBeanFromText("{name:'n',secret:'s'}", SimpleBean.class);
        assertEquals("n", parsed.getName());
        assertNull(parsed.getSecret());
    }

    @Test
    public void testJsonIgnoreOnSetter() {
        SetterIgnoredBean bean = new SetterIgnoredBean();
        bean.setName("n");
        bean.setSecret("s");
        assertEquals("{\"name\":\"n\"}", JsonTool.stringify(bean));

        SetterIgnoredBean parsed = JsonTool.parseBeanFromText("{name:'n',secret:'s'}", SetterIgnoredBean.class);
        assertEquals("n", parsed.getName());
        assertNull(parsed.getSecret());
    }

    @Test
    public void testJsonIgnoreOnPrivateField() {
        PrivateFieldIgnoredBean bean = new PrivateFieldIgnoredBean();
        bean.setName("n");
        bean.setSecret("s");
        assertEquals("{\"name\":\"n\"}", JsonTool.stringify(bean));

        PrivateFieldIgnoredBean parsed = JsonTool.parseBeanFromText("{name:'n',secret:'s'}",
                PrivateFieldIgnoredBean.class);
        assertEquals("n", parsed.getName());
        assertNull(parsed.getSecret());
    }

    @Test
    public void testJsonIgnoreInheritedByOverride() {
        ChildBean bean = new ChildBean();
        bean.setName("n");
        bean.setRole("admin");
        assertEquals("{\"name\":\"n\"}", JsonTool.stringify(bean));

        ChildBean parsed = JsonTool.parseBeanFromText("{name:'n',role:'admin'}", ChildBean.class);
        assertEquals("n", parsed.getName());
        assertEquals("user", parsed.getRole());
    }

    @Test
    public void testJsonIgnoreValueFalse() {
        ValueFalseBean bean = new ValueFalseBean();
        bean.setName("n");
        bean.setVisible("v");
        assertEquals("{\"name\":\"n\",\"visible\":\"v\"}", JsonTool.stringify(bean));

        ValueFalseBean parsed = JsonTool.parseBeanFromText("{name:'n',visible:'v'}", ValueFalseBean.class);
        assertEquals("v", parsed.getVisible());
    }

    @Test
    public void testJsonIgnorePropertyStillAbsorbedByAnySetter() {
        AnySetterBean bean = JsonTool.parseBeanFromText("{name:'n',secret:'s'}", AnySetterBean.class);
        assertEquals("n", bean.getName());
        assertEquals("s", bean.getExtras().get("secret"));
    }

    @Test
    public void testOnlySerializableTargetFalseReadsIgnoredProperty() {
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("name", "n");
        src.put("secret", "s");

        SimpleBean bean = new SimpleBean();
        BeanCopyOptions options = new BeanCopyOptions();
        options.setOnlySerializableTarget(false);
        BeanCopier.INSTANCE.copyBean(src, bean, ReflectionManager.instance().buildRawType(SimpleBean.class), false,
                options);

        assertEquals("n", bean.getName());
        assertEquals("s", bean.getSecret());
    }

    @Test
    public void testJsonAliasSingleAndMultiple() {
        AliasBean a = JsonTool.parseBeanFromText("{a:'x'}", AliasBean.class);
        assertEquals("x", a.getName());

        AliasBean b = JsonTool.parseBeanFromText("{b:'y'}", AliasBean.class);
        assertEquals("y", b.getName());

        AliasBean n = JsonTool.parseBeanFromText("{name:'z'}", AliasBean.class);
        assertEquals("z", n.getName());
    }

    @Test
    public void testJsonAliasWriteUsesPrimaryName() {
        AliasBean bean = new AliasBean();
        bean.setName("x");
        assertEquals("{\"name\":\"x\"}", JsonTool.stringify(bean));
    }

    @Test
    public void testJsonAliasConflictIsFailFast() {
        assertThrows(NopException.class,
                () -> ReflectionManager.instance().getBeanModelForClass(AliasConflictBean.class));
    }

    @Test
    public void testIgnoredPropertyMetadata() {
        assertFalse(ReflectionManager.instance().getBeanModelForClass(SimpleBean.class)
                .getPropertyModel("secret").isSerializable());
        assertTrue(ReflectionManager.instance().getBeanModelForClass(SimpleBean.class)
                .getPropertyModel("name").isSerializable());
    }
}
