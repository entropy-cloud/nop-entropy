/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.BeanCopyOptions;
import io.nop.core.reflect.bean.BeanModel;
import io.nop.core.reflect.bean.TreeBeanBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJacksonClassLevelAnnotations {

    @JsonIgnoreProperties({"secret", "renamed"})
    @DataBean
    public static class IgnorePropertiesBean {
        private String name;
        private String secret;
        private String aliasName;

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

        @JsonProperty("renamed")
        public String getAliasName() {
            return aliasName;
        }

        public void setAliasName(String aliasName) {
            this.aliasName = aliasName;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgnoreUnknownBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class StrictBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonIgnoreType
    @DataBean
    public static class IgnoredType {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @DataBean
    public static class TypedBean {
        private String name;
        private IgnoredType ignored;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public IgnoredType getIgnored() {
            return ignored;
        }

        public void setIgnored(IgnoredType ignored) {
            this.ignored = ignored;
        }
    }

    @JsonIgnoreProperties({"secret"})
    @DataBean
    public static class ExtBean {
        private String name;
        private final Map<String, Object> extras = new LinkedHashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSecret() {
            return "hidden";
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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @DataBean
    public static class ClassIncludeBean {
        private String name;
        private String empty = "";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String getEmpty() {
            return empty;
        }

        public void setEmpty(String empty) {
            this.empty = empty;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @DataBean
    public static class ClassAbsentBean {
        private Optional<String> empty = Optional.empty();
        private String name = "n";

        public Optional<String> getEmpty() {
            return empty;
        }

        public void setEmpty(Optional<String> empty) {
            this.empty = empty;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonIgnoreProperties({"secret"})
    public static class ParentBean {
        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    @DataBean
    public static class ChildBean extends ParentBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @DataBean
    public static class TreeBeanTarget {
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

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    @JsonIgnoreProperties({"secret"})
    public static class TreeBeanIgnoreTarget extends TreeBeanTarget {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TreeBeanIgnoreUnknownTarget {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    public void testIgnorePropertiesByName() {
        IgnorePropertiesBean bean = new IgnorePropertiesBean();
        bean.setName("n");
        bean.setSecret("s");
        bean.setAliasName("a");

        assertEquals("{\"name\":\"n\"}", JsonTool.stringify(bean));

        IgnorePropertiesBean parsed = JsonTool.parseBeanFromText("{name:'n',secret:'s',renamed:'a'}",
                IgnorePropertiesBean.class);
        assertEquals("n", parsed.getName());
        assertNull(parsed.getSecret());
        assertNull(parsed.getAliasName());
    }

    @Test
    public void testIgnoreUnknown() {
        IgnoreUnknownBean bean = JsonTool.parseBeanFromText("{name:'n',extra:1}", IgnoreUnknownBean.class);
        assertEquals("n", bean.getName());

        assertThrows(NopException.class, () -> JsonTool.parseBeanFromText("{name:'n',extra:1}", StrictBean.class));
    }

    @Test
    public void testIgnoreTypeAsProperty() {
        TypedBean bean = new TypedBean();
        bean.setName("n");

        assertEquals("{\"name\":\"n\"}", JsonTool.stringify(bean));

        TypedBean parsed = JsonTool.parseBeanFromText("{name:'n',ignored:{value:'x'}}", TypedBean.class);
        assertEquals("n", parsed.getName());
        assertNull(parsed.getIgnored());
    }

    @Test
    public void testIgnorePropertiesNotAbsorbedByAnySetter() {
        ExtBean bean = JsonTool.parseBeanFromText("{name:'n',secret:'s'}", ExtBean.class);
        assertEquals("n", bean.getName());
        assertNull(bean.getExtras().get("secret"));
    }

    @Test
    public void testClassLevelIncludeOutput() {
        ClassIncludeBean bean = new ClassIncludeBean();
        bean.setName("n");
        assertEquals("{\"empty\":\"\",\"name\":\"n\"}", JsonTool.stringify(bean));
    }

    @Test
    public void testClassLevelNonAbsent() {
        assertEquals("{\"name\":\"n\"}", JsonTool.stringify(new ClassAbsentBean()));
    }

    @Test
    public void testParentClassAnnotationNotInherited() {
        BeanModel childModel = (BeanModel) ReflectionManager.instance().getBeanModelForClass(ChildBean.class);
        assertTrue(childModel.getIgnoredPropertyNames().isEmpty());

        ChildBean bean = new ChildBean();
        bean.setName("n");
        bean.setSecret("s");
        assertEquals("{\"name\":\"n\",\"secret\":\"s\"}", JsonTool.stringify(bean));
    }

    @Test
    public void testTreeBeanIgnoreProperties() {
        TreeBean node = new TreeBean();
        node.setTagName("node");
        node.setAttr("name", "n");
        node.setAttr("secret", "s");

        Object bean = TreeBeanBuilder.INSTANCE.buildBeanFromTreeBean(node,
                ReflectionManager.instance().buildRawType(TreeBeanIgnoreTarget.class), BeanCopyOptions.DEFAULT);
        TreeBeanIgnoreTarget target = (TreeBeanIgnoreTarget) bean;
        assertEquals("n", target.getName());
        assertNull(target.getSecret());
    }

    @Test
    public void testTreeBeanIgnoreUnknown() {
        TreeBean node = new TreeBean();
        node.setTagName("node");
        node.setAttr("name", "n");
        node.setAttr("extra", "x");

        Object bean = TreeBeanBuilder.INSTANCE.buildBeanFromTreeBean(node,
                ReflectionManager.instance().buildRawType(TreeBeanIgnoreUnknownTarget.class), BeanCopyOptions.DEFAULT);
        assertNotNull(bean);
        assertEquals("n", ((TreeBeanIgnoreUnknownTarget) bean).getName());
    }
}
