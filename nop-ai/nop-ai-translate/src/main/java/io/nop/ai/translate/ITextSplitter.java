package io.nop.ai.translate;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

public interface ITextSplitter {
    @DataBean
    class SplitChunk {
        private final String prolog;
        private final String content;

        public SplitChunk(String prolog, String content) {
            this.prolog = prolog;
            this.content = content;
        }

        public String getProlog() {
            return prolog;
        }

        public String getContent() {
            return content;
        }
    }

    List<SplitChunk> split(String text, int prologSize, int maxContentSize);
}