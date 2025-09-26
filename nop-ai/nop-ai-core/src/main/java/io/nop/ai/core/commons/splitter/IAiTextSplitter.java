package io.nop.ai.core.commons.splitter;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.SourceLocation;
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

    @DataBean
    class SplitOptions {
        private int maxContentSize;
        private int overlapSize;
        private int maxElementsPerChunk;
        private boolean ignoreParseError;
        private boolean splitByLine;

        public static SplitOptions create(int maxContentSize) {
            SplitOptions options = new SplitOptions();
            options.setMaxContentSize(maxContentSize);
            return options;
        }

        public SplitOptions splitByLine(boolean b){
            setSplitByLine(b);
            return this;
        }

        public SplitOptions overlapSize(int overlapSize) {
            this.overlapSize = overlapSize;
            return this;
        }

        public boolean isSplitByLine() {
            return splitByLine;
        }

        public void setSplitByLine(boolean splitByLine) {
            this.splitByLine = splitByLine;
        }

        public int getOverlapSize() {
            return overlapSize;
        }

        public void setOverlapSize(int overlapSize) {
            this.overlapSize = overlapSize;
        }

        public SplitOptions maxSubParts(int maxSubParts) {
            this.maxElementsPerChunk = maxSubParts;
            return this;
        }

        public SplitOptions ignoreParseError(boolean b) {
            this.ignoreParseError = b;
            return this;
        }

        public int getMaxContentSize() {
            return maxContentSize;
        }

        public void setMaxContentSize(int maxContentSize) {
            this.maxContentSize = maxContentSize;
        }

        public int getMaxElementsPerChunk() {
            return maxElementsPerChunk;
        }

        public void setMaxElementsPerChunk(int maxElementsPerChunk) {
            this.maxElementsPerChunk = maxElementsPerChunk;
        }

        public boolean isIgnoreParseError() {
            return ignoreParseError;
        }

        public void setIgnoreParseError(boolean ignoreParseError) {
            this.ignoreParseError = ignoreParseError;
        }
    }

    List<SplitChunk> split(SourceLocation loc, String text, SplitOptions options);
}