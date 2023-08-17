/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text.marker;

import java.util.function.Supplier;

public class Markers {

    /**
     * 给文本区间附加一个名称
     */
    public static class NameMarker extends Marker {
        private final String name;

        public NameMarker(int textStart, int textEnd, String name) {
            super(textStart, textEnd);
            this.name = name;
        }

        private static final long serialVersionUID = -3330721940392200563L;

        @Override
        public NameMarker offset(int offset) {
            if (offset == 0)
                return this;
            return new NameMarker(textBegin + offset, textEnd + offset, name);
        }

        public String getName() {
            return name;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            return appendPos(sb).append(':').append('@').append(name).toString();
        }
    }

    /**
     * 给文本区间附加一个值
     */
    public static class ValueMarker extends Marker {

        private static final long serialVersionUID = -8974432479354034511L;

        private final String name;
        private final Object value;
        private final boolean masked;

        public ValueMarker(int textStart, int textEnd, String name, Object value, boolean masked) {
            super(textStart, textEnd);
            this.name = name;
            this.value = value;
            this.masked = masked;
        }

        public ValueMarker(int pos, Object value, boolean masked) {
            this(pos, pos + 1, null, value, masked);
        }

        public String getName() {
            return name;
        }

        @Override
        public final ValueMarker offset(int offset) {
            if (offset == 0)
                return this;
            return newValueMarker(textBegin + offset, textEnd + offset, value);
        }

        protected ValueMarker newValueMarker(int begin, int end, Object value) {
            return new ValueMarker(begin, end, name, value, masked);
        }

        public Object getValue() {
            return value;
        }

        public boolean isMasked() {
            return masked;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            return appendPos(sb).append(':').append(value).toString();
        }

        public final ValueMarker changeValue(Object value) {
            return newValueMarker(textBegin, textEnd, value);
        }

        public final ValueMarker changeValue(int offset, Object value) {
            return newValueMarker(textBegin + offset, textEnd + offset, value);
        }
    }

    /**
     * 给文本区间附加一个Provider，可以执行provider返回一个值或者ValueMarker
     */
    public static class ProviderMarker extends Marker {

        private static final long serialVersionUID = -8974432479354034511L;

        private final String name;
        private final Supplier<?> provider;
        private final boolean masked;

        public ProviderMarker(int textStart, int textEnd, String name, Supplier<?> provider, boolean masked) {
            super(textStart, textEnd);
            this.name = name;
            this.provider = provider;
            this.masked = masked;
        }

        public ProviderMarker(int pos, Supplier<?> provider, boolean masked) {
            this(pos, pos + 1, null, provider,masked);
        }

        public boolean isMasked(){
            return masked;
        }

        public String getName() {
            return name;
        }

        @Override
        public final ProviderMarker offset(int offset) {
            if (offset == 0)
                return this;
            return new ProviderMarker(textBegin + offset, textEnd + offset, name, provider,masked);
        }

        public Supplier<?> getProvider() {
            return provider;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            return appendPos(sb).append(':').append(name).toString();
        }

        public Object getValue() {
            return provider.get();
        }

        public final ValueMarker buildValueMarker() {
            Object value = provider.get();
            return new ValueMarker(textBegin, textEnd, name, value, masked);
        }
    }
}