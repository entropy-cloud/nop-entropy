/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.text.RawText;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJacksonRawValue {

    @DataBean
    public static class RawBean {
        private String data = "{\"a\":1}";
        private String text = "hello";
        private Integer number = 3;

        @JsonRawValue
        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @JsonRawValue
        public Integer getNumber() {
            return number;
        }

        public void setNumber(Integer number) {
            this.number = number;
        }
    }

    @DataBean
    public static class NullRawBean {
        private String data;

        @JsonRawValue
        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }
    }

    @Test
    public void testRawValue() {
        RawBean bean = new RawBean();
        assertEquals("{\"data\":{\"a\":1},\"number\":3,\"text\":\"hello\"}", JsonTool.stringify(bean));
    }

    @Test
    public void testRawValueReadUnchanged() {
        RawBean parsed = JsonTool.parseBeanFromText("{\"data\":\"{\\\"a\\\":1}\"}", RawBean.class);
        assertEquals("{\"a\":1}", parsed.getData());
    }

    @Test
    public void testRawTextStillUnescaped() {
        assertEquals("<b>x</b>", JsonTool.stringify(new RawText("<b>x</b>")));
    }

    @Test
    public void testRawValueNull() {
        assertEquals("{\"data\":null}", JsonTool.stringify(new NullRawBean()));
    }
}
