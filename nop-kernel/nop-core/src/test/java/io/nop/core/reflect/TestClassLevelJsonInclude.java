package io.nop.core.reflect;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestClassLevelJsonInclude {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class BeanWithClassInclude {
        private String name;
        private String desc;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class BeanWithMixedInclude {
        private String name;
        private String desc;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }
    }

    public static class BeanWithoutClassInclude {
        private String name;
        private String desc;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }
    }

    @Test
    public void testClassLevelIncludeAppliedToAllProps() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(BeanWithClassInclude.class);
        IBeanPropertyModel nameProp = beanModel.getPropertyModel("name");
        IBeanPropertyModel descProp = beanModel.getPropertyModel("desc");

        assertEquals(JsonInclude.Include.NON_EMPTY, nameProp.getJsonInclude());
        assertEquals(JsonInclude.Include.NON_EMPTY, descProp.getJsonInclude());
    }

    @Test
    public void testMethodLevelOverridesClassLevel() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(BeanWithMixedInclude.class);
        IBeanPropertyModel nameProp = beanModel.getPropertyModel("name");
        IBeanPropertyModel descProp = beanModel.getPropertyModel("desc");

        assertEquals(JsonInclude.Include.NON_NULL, nameProp.getJsonInclude());
        assertEquals(JsonInclude.Include.NON_EMPTY, descProp.getJsonInclude());
    }

    @Test
    public void testNoClassLevelInclude() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(BeanWithoutClassInclude.class);
        IBeanPropertyModel nameProp = beanModel.getPropertyModel("name");
        IBeanPropertyModel descProp = beanModel.getPropertyModel("desc");

        assertNull(nameProp.getJsonInclude());
        assertEquals(JsonInclude.Include.NON_EMPTY, descProp.getJsonInclude());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BeanWithNonNullClassInclude {
        private String name;
        private String desc;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }
    }

    @Test
    public void testClassLevelNonNull() {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(BeanWithNonNullClassInclude.class);
        IBeanPropertyModel nameProp = beanModel.getPropertyModel("name");
        IBeanPropertyModel descProp = beanModel.getPropertyModel("desc");

        assertEquals(JsonInclude.Include.NON_NULL, nameProp.getJsonInclude());
        assertEquals(JsonInclude.Include.NON_NULL, descProp.getJsonInclude());
    }
}
