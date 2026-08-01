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

        /**
         * @return the chunk identifier, or null when not assigned
         */
        public String getChunkId() {
            return chunkId;
        }

        /**
         * @return the chunk type (e.g. "text"), or null for a plain content chunk
         */
        public String getType() {
            return type;
        }

        /**
         * @return the chunk content
         */
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

        /**
         * @param maxContentSize the maximum character size of a chunk
         * @return options with only the chunk size limit configured
         */
        public static SplitOptions create(int maxContentSize) {
            SplitOptions options = new SplitOptions();
            options.setMaxContentSize(maxContentSize);
            return options;
        }

        /**
         * Enables line-aligned chunking (chunk boundaries are whole lines).
         */
        public SplitOptions splitByLine(boolean b){
            setSplitByLine(b);
            return this;
        }

        /**
         * Sets the number of characters shared between adjacent chunks.
         */
        public SplitOptions overlapSize(int overlapSize) {
            this.overlapSize = overlapSize;
            return this;
        }

        /**
         * @return true if chunk boundaries must be aligned to whole lines
         */
        public boolean isSplitByLine() {
            return splitByLine;
        }

        public void setSplitByLine(boolean splitByLine) {
            this.splitByLine = splitByLine;
        }

        /**
         * @return the number of characters shared between adjacent chunks
         */
        public int getOverlapSize() {
            return overlapSize;
        }

        public void setOverlapSize(int overlapSize) {
            this.overlapSize = overlapSize;
        }

        /**
         * Sets the maximum number of sub-parts (e.g. elements) collected per chunk.
         */
        public SplitOptions maxSubParts(int maxSubParts) {
            this.maxElementsPerChunk = maxSubParts;
            return this;
        }

        /**
         * Configures whether parse errors in structured input are ignored.
         */
        public SplitOptions ignoreParseError(boolean b) {
            this.ignoreParseError = b;
            return this;
        }

        /**
         * @return the maximum character size of a chunk
         */
        public int getMaxContentSize() {
            return maxContentSize;
        }

        public void setMaxContentSize(int maxContentSize) {
            this.maxContentSize = maxContentSize;
        }

        /**
         * @return the maximum number of sub-parts collected per chunk (0 = no limit)
         */
        public int getMaxElementsPerChunk() {
            return maxElementsPerChunk;
        }

        public void setMaxElementsPerChunk(int maxElementsPerChunk) {
            this.maxElementsPerChunk = maxElementsPerChunk;
        }

        /**
         * @return true if parse errors in structured input are ignored
         */
        public boolean isIgnoreParseError() {
            return ignoreParseError;
        }

        public void setIgnoreParseError(boolean ignoreParseError) {
            this.ignoreParseError = ignoreParseError;
        }
    }

    /**
     * Splits the given text into chunks according to the split options.
     * <p>
     * When {@code options.maxContentSize} is not exceeded the whole text is returned as a single chunk.
     * With {@code options.splitByLine} enabled, chunk boundaries are aligned to whole lines and an
     * empty text yields one empty {@code "text"} chunk.
     *
     * @param loc     the source location of the text, used for diagnostics
     * @param text    the text to split
     * @param options the split options (chunk size, overlap, line alignment, ...)
     * @return the resulting chunks in order
     */
    List<SplitChunk> split(SourceLocation loc, String text, SplitOptions options);
}