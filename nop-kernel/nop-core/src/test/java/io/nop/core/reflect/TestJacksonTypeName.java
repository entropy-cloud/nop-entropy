/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.IBeanModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJacksonTypeName {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(Dog.class),
            @JsonSubTypes.Type(Cat.class)
    })
    public abstract static class Animal {
        private String type;
        private String name;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonTypeName("dog")
    public static class Dog extends Animal {
    }

    @JsonTypeName("cat")
    public static class Cat extends Animal {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ExplicitDog.class, name = "explicit"),
            @JsonSubTypes.Type(value = ImplicitCat.class)
    })
    public abstract static class ExplicitAnimal {
    }

    @JsonTypeName("implicit")
    public static class ExplicitDog extends ExplicitAnimal {
    }

    @JsonTypeName("cat-implicit")
    public static class ImplicitCat extends ExplicitAnimal {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(FirstEmpty.class),
            @JsonSubTypes.Type(SecondEmpty.class)
    })
    public abstract static class DuplicateAnimal {
    }

    public static class FirstEmpty extends DuplicateAnimal {
    }

    public static class SecondEmpty extends DuplicateAnimal {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(@JsonSubTypes.Type(SingleEmpty.class))
    public abstract static class LegacyAnimal {
    }

    public static class SingleEmpty extends LegacyAnimal {
    }

    @Test
    public void testJsonTypeNameFallback() {
        IBeanModel model = ReflectionManager.instance().getBeanModelForClass(Animal.class);
        assertTrue(model.determineSubType("dog") != null);
        assertTrue(model.determineSubType("cat") != null);

        Animal dog = JsonTool.parseBeanFromText("{type:'dog',name:'d'}", Animal.class);
        assertEquals(Dog.class, dog.getClass());
        assertEquals("d", dog.getName());
    }

    @Test
    public void testExplicitNameWins() {
        IBeanModel model = ReflectionManager.instance().getBeanModelForClass(ExplicitAnimal.class);
        assertTrue(model.determineSubType("explicit") != null);
        assertTrue(model.determineSubType("implicit") == null);
        assertTrue(model.determineSubType("cat-implicit") != null);
    }

    @Test
    public void testDuplicateEmptyNameFailsFast() {
        assertThrows(NopException.class,
                () -> ReflectionManager.instance().getBeanModelForClass(DuplicateAnimal.class));
    }

    @Test
    public void testSingleEmptyNameKeptForCompatibility() {
        IBeanModel model = ReflectionManager.instance().getBeanModelForClass(LegacyAnimal.class);
        assertTrue(model.determineSubType("") != null);
    }
}
