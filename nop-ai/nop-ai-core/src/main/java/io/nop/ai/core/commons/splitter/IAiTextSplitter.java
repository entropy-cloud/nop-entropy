package io.nop.ai.core.commons.splitter;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;

import java.util.List;

public interface IAiTextSplitter {
    @DataBean
    class SplitChunk {
        private final String type;
        private final String content;
        private final String chunkId;

        public SplitChunk(@JsonProperty("type") String type,
                          @JsonProperty("content") String content,
                          @JsonProperty("chunkId") String chunkId) {
            this.type = type;
            this.content = content;
            this.chunkId = chunkId;
        }

        public SplitChunk(String type, String content) {
            this(type, content, null);
        }

        public String toString() {
            return StringHelper.toString(content, "");
        }

        public String getChunkId() {
            return chunkId;
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