/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJacksonIncludeSemantics {

    @DataBean
    public static class AbsentBean {
        private Optional<String> optional = Optional.empty();
        private OptionalInt optionalInt = OptionalInt.empty();
        private OptionalLong optionalLong = OptionalLong.empty();
        private OptionalDouble optionalDouble = OptionalDouble.empty();
        private AtomicReference<String> reference = new AtomicReference<>();
        private String regular = "text";

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public Optional<String> getOptional() {
            return optional;
        }

        public void setOptional(Optional<String> optional) {
            this.optional = optional;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public OptionalInt getOptionalInt() {
            return optionalInt;
        }

        public void setOptionalInt(OptionalInt optionalInt) {
            this.optionalInt = optionalInt;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public OptionalLong getOptionalLong() {
            return optionalLong;
        }

        public void setOptionalLong(OptionalLong optionalLong) {
            this.optionalLong = optionalLong;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public OptionalDouble getOptionalDouble() {
            return optionalDouble;
        }

        public void setOptionalDouble(OptionalDouble optionalDouble) {
            this.optionalDouble = optionalDouble;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public AtomicReference<String> getReference() {
            return reference;
        }

        public void setReference(AtomicReference<String> reference) {
            this.reference = reference;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public String getRegular() {
            return regular;
        }

        public void setRegular(String regular) {
            this.regular = regular;
        }
    }

    @DataBean
    public static class EmptyBean {
        private String text = "";
        private List<String> list = Collections.emptyList();
        private Map<String, String> map = Collections.emptyMap();
        private String nonEmpty = "x";
        private List<String> nonEmptyList = Collections.singletonList("a");

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<String> getList() {
            return list;
        }

        public void setList(List<String> list) {
            this.list = list;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Map<String, String> getMap() {
            return map;
        }

        public void setMap(Map<String, String> map) {
            this.map = map;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String getNonEmpty() {
            return nonEmpty;
        }

        public void setNonEmpty(String nonEmpty) {
            this.nonEmpty = nonEmpty;
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<String> getNonEmptyList() {
            return nonEmptyList;
        }

        public void setNonEmptyList(List<String> nonEmptyList) {
            this.nonEmptyList = nonEmptyList;
        }
    }

    @DataBean
    public static class DefaultBean {
        private int count = 0;
        private int other = 1;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public int getOther() {
            return other;
        }

        public void setOther(int other) {
            this.other = other;
        }
    }

    @DataBean
    public static class PresentBean {
        private Optional<String> optional = Optional.of("v");
        private OptionalInt optionalInt = OptionalInt.of(3);
        private AtomicReference<String> reference = new AtomicReference<>("r");

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public Optional<String> getOptional() {
            return optional;
        }

        public void setOptional(Optional<String> optional) {
            this.optional = optional;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public OptionalInt getOptionalInt() {
            return optionalInt;
        }

        public void setOptionalInt(OptionalInt optionalInt) {
            this.optionalInt = optionalInt;
        }

        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        public AtomicReference<String> getReference() {
            return reference;
        }

        public void setReference(AtomicReference<String> reference) {
            this.reference = reference;
        }
    }

    @Test
    public void testNonAbsent() {
        AbsentBean bean = new AbsentBean();
        assertEquals("{\"regular\":\"text\"}", JsonTool.stringify(bean));

        assertEquals("{\"optional\":\"v\",\"optionalInt\":3,\"reference\":\"r\"}",
                JsonTool.stringify(new PresentBean()));
    }

    @Test
    public void testNonEmpty() {
        EmptyBean bean = new EmptyBean();
        assertEquals("{\"nonEmpty\":\"x\",\"nonEmptyList\":[\"a\"]}", JsonTool.stringify(bean));
    }

    @Test
    public void testNonDefault() {
        DefaultBean bean = new DefaultBean();
        assertEquals("{}", JsonTool.stringify(bean));

        bean.setCount(2);
        assertEquals("{\"count\":2}", JsonTool.stringify(bean));
    }
}
