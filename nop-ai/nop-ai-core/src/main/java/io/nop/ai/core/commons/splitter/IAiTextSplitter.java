package io.nop.ai.core.commons.splitter;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

public interface IAiTextSplitter {
    @DataBean
    class SplitChunk {
        private final String type;
        private final String content;

        public SplitChunk(String type, String content) {
            this.type = type;
            this.content = content;
        }

        public String getType() {
            return type;
        }

        public String getContent() {
            return content;
        }
    }

    List<SplitChunk> split(String text, int maxContentSize);
}