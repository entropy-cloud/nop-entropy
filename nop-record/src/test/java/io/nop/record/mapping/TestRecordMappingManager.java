package io.nop.record.mapping;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRecordMappingManager extends JunitBaseTestCase {
    @Inject
    IRecordMappingManager recordMappingManager;

    @Test
    public void testMapping() {
        IRecordMapping mapping = recordMappingManager.getRecordMapping("test.demo.Type1ToType2");

        Type1 t1 = new Type1();
        t1.setField1("f1");
        t1.setField4("f4");
        SubType s1 = new SubType();
        s1.setField2("f2");
        t1.setSub(s1);

        Type2 t2 = new Type2();
        mapping.map(t1, t2, new RecordMappingContext());

        assertEquals("{\"base\":{\"name1\":\"f1\"},\"ext\":{\"name2\":\"f2\",\"name3\":123},\"name4\":\"f4\"}", JsonTool.stringify(t2));

        Map<String,Object> map = new LinkedHashMap<>();
        mapping.map(t1, map, new RecordMappingContext());
        assertEquals("{\"base\":{\"name1\":\"f1\"},\"ext\":{\"name2\":\"f2\",\"name3\":123},\"name4\":\"f4\"}", JsonTool.stringify(map));
    }

    @DataBean
    static class Type2 {
        String name4;
        BaseType base;
        ExtType ext;

        public String getName4() {
            return name4;
        }

        public void setName4(String name4) {
            this.name4 = name4;
        }

        public BaseType makeBase() {
            if (base == null)
                base = new BaseType();
            return base;
        }

        public BaseType getBase() {
            return base;
        }

        public void setBase(BaseType base) {
            this.base = base;
        }

        public ExtType makeExt() {
            if (ext == null)
                ext = new ExtType();
            return ext;
        }

        public ExtType getExt() {
            return ext;
        }

        public void setExt(ExtType ext) {
            this.ext = ext;
        }
    }

    @DataBean
    static class BaseType {
        String name1;

        public void setName1(String name1) {
            this.name1 = name1;
        }

        public String getName1() {
            return name1;
        }
    }

    @DataBean
    static class ExtType {
        String name2;
        Integer name3;

        public String getName2() {
            return name2;
        }

        public void setName2(String name2) {
            this.name2 = name2;
        }

        public Integer getName3() {
            return name3;
        }

        public void setName3(Integer name3) {
            this.name3 = name3;
        }
    }

    @DataBean
    static class Type1 {
        String field1;
        String field4;
        SubType sub;

        public String getField1() {
            return field1;
        }

        public void setField1(String field1) {
            this.field1 = field1;
        }

        public String getField4() {
            return field4;
        }

        public void setField4(String field4) {
            this.field4 = field4;
        }

        public SubType getSub() {
            return sub;
        }

        public void setSub(SubType sub) {
            this.sub = sub;
        }
    }

    @DataBean
    static class SubType {
        String field2;
        Integer field3;

        public String getField2() {
            return field2;
        }

        public void setField2(String field2) {
            this.field2 = field2;
        }

        public Integer getField3() {
            return field3;
        }

        public void setField3(Integer field3) {
            this.field3 = field3;
        }
    }
}
