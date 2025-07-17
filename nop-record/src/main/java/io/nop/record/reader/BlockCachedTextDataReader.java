package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;

import java.io.IOException;
import java.util.LinkedList;

import static io.nop.record.RecordErrors.ERR_RECORD_NO_ENOUGH_DATA;

/**
 * 基于块链表的缓存文本数据读取器
 * 维护一个滚动窗口，所有数据必须先读取到缓存中才能访问
 * 只支持在缓存窗口范围内的向后seek，超出范围直接报错
 */
public class BlockCachedTextDataReader implements ITextDataReader {

    // 默认配置常量
    private static final int DEFAULT_BLOCK_SIZE = 4096;  // 默认文本块大小
    private static final long DEFAULT_MAX_SKIP_DISTANCE = 1024 * 1024; // 1MB
    private static final int DEFAULT_MAX_CACHE_BLOCKS = 8;
    private static final int DEFAULT_BACKWARD_CACHE_BLOCKS = 2; // 保留的向后缓存块数量

    /**
     * 文本数据块类，包装文本内容和相关元数据
     */
    protected static class TextBlock {
        private final String content;
        private final long startPosition;  // 该block在整个流中的起始位置
        private final int size;            // 实际字符数

        public TextBlock(String content, long startPosition) {
            this.content = content != null ? content : "";
            this.startPosition = startPosition;
            this.size = this.content.length();
        }

        public String getContent() {
            return content;
        }

        public long getStartPosition() {
            return startPosition;
        }

        public long getEndPosition() {
            return startPosition + size;
        }

        public int getSize() {
            return size;
        }

        /**
         * 检查指定位置是否在此block范围内
         */
        public boolean contains(long position) {
            return position >= startPosition && position < getEndPosition();
        }

        /**
         * 获取指定位置在此block中的相对偏移
         */
        public int getRelativeOffset(long position) {
            if (!contains(position)) {
                throw new IllegalArgumentException("Position " + position + " not in block range ["
                        + startPosition + ", " + getEndPosition() + ")");
            }
            return (int) (position - startPosition);
        }

        /**
         * 获取从指定位置开始的子字符串
         */
        public String substring(long fromPosition, long toPosition) {
            if (!contains(fromPosition)) {
                throw new IllegalArgumentException("FromPosition " + fromPosition + " not in block");
            }

            int fromOffset = getRelativeOffset(fromPosition);
            int toOffset = toPosition >= getEndPosition() ? size : getRelativeOffset(toPosition);

            return content.substring(fromOffset, toOffset);
        }

        /**
         * 从指定位置查找换行符
         */
        public int findNewlineFrom(long fromPosition, int maxLength) {
            if (!contains(fromPosition)) {
                return -1;
            }

            int startOffset = getRelativeOffset(fromPosition);
            int endOffset = Math.min(startOffset + maxLength, size);

            for (int i = startOffset; i < endOffset; i++) {
                char c = content.charAt(i);
                if (c == '\r' || c == '\n') {
                    return i - startOffset;
                }
            }
            return -1;
        }
    }

    private final ITextDataReader underlyingReader;
    private final int defaultBlockSize;
    private final int maxCacheBlocks;       // 最大缓存block数量
    private final long maxSkipDistance;     // 最大允许的skip距离
    private final int backwardCacheBlocks;  // 保留的向后缓存块数量

    private final LinkedList<TextBlock> cachedBlocks = new LinkedList<>();
    private long currentPosition = 0;       // 当前在整个流中的位置
    private long maxReadPosition = 0;       // 已读取到的最大位置
    private long underlyingPosition = 0;    // 底层reader的当前位置
    private boolean eofReached = false;     // 是否已到达文件末尾

    /**
     * 完整构造函数
     */
    public BlockCachedTextDataReader(ITextDataReader underlyingReader,
                                     int defaultBlockSize,
                                     int maxCacheBlocks,
                                     long maxSkipDistance,
                                     int backwardCacheBlocks) {
        if (underlyingReader == null) {
            throw new IllegalArgumentException("underlyingReader cannot be null");
        }
        if (defaultBlockSize <= 0) {
            throw new IllegalArgumentException("defaultBlockSize must be positive");
        }
        if (maxCacheBlocks <= 0) {
            throw new IllegalArgumentException("maxCacheBlocks must be positive");
        }
        if (backwardCacheBlocks < 0) {
            throw new IllegalArgumentException("backwardCacheBlocks cannot be negative");
        }

        this.underlyingReader = underlyingReader;
        this.defaultBlockSize = defaultBlockSize;
        this.maxCacheBlocks = maxCacheBlocks;
        this.maxSkipDistance = maxSkipDistance;
        this.backwardCacheBlocks = backwardCacheBlocks;
    }

    /**
     * 便利构造函数
     */
    public BlockCachedTextDataReader(ITextDataReader underlyingReader, int defaultBlockSize) {
        this(underlyingReader, defaultBlockSize, DEFAULT_MAX_CACHE_BLOCKS,
                DEFAULT_MAX_SKIP_DISTANCE, DEFAULT_BACKWARD_CACHE_BLOCKS);
    }

    /**
     * 默认配置构造函数
     */
    public BlockCachedTextDataReader(ITextDataReader underlyingReader) {
        this(underlyingReader, DEFAULT_BLOCK_SIZE, DEFAULT_MAX_CACHE_BLOCKS,
                DEFAULT_MAX_SKIP_DISTANCE, DEFAULT_BACKWARD_CACHE_BLOCKS);
    }

    @Override
    public long available() {
        try {
            long cachedRemaining = maxReadPosition - currentPosition;
            if (eofReached) {
                return Math.max(0, cachedRemaining);
            }
            return cachedRemaining + underlyingReader.available();
        } catch (Exception e) {
            return maxReadPosition - currentPosition;
        }
    }

    @Override
    public void skip(int n) {
        if (n < 0) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA);
        }
        if (n == 0) return;

        // 检查skip距离是否合理
        if (n > maxSkipDistance) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                    .param("reason", "Skip distance " + n + " exceeds maximum allowed " + maxSkipDistance);
        }

        long targetPos = currentPosition + n;

        try {
            // 确保目标位置的数据已经被读取到缓存中
            if (targetPos > maxReadPosition) {
                ensureDataReadTo(targetPos);
            }

            currentPosition = targetPos;
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    @Override
    public boolean isEof() {
        try {
            // 如果当前位置已经到达最大读取位置，尝试读取更多数据
            if (currentPosition >= maxReadPosition && !eofReached) {
                tryLoadMoreData();
            }
            return eofReached && currentPosition >= maxReadPosition;
        } catch (IOException e) {
            return true; // 读取异常时视为EOF
        }
    }

    @Override
    public String readFully(int len) {
        if (len < 0) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA);
        }
        if (len == 0) {
            return "";
        }

        try {
            // 确保目标范围的数据已被读取
            long targetPos = currentPosition + len;
            if (targetPos > maxReadPosition) {
                ensureDataReadTo(targetPos);
            }

            if (currentPosition + len > maxReadPosition) {
                throw new NopException(ERR_RECORD_NO_ENOUGH_DATA);
            }

            // 检查当前位置是否在缓存窗口内
            if (!isPositionInCache(currentPosition)) {
                throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                        .param("reason", "Position " + currentPosition + " outside cache window");
            }

            StringBuilder result = new StringBuilder();
            long remaining = len;
            long pos = currentPosition;

            while (remaining > 0 && pos < maxReadPosition) {
                TextBlock currentBlock = findBlockForPosition(pos);
                if (currentBlock == null) {
                    break;
                }

                long endPosInBlock = Math.min(pos + remaining, currentBlock.getEndPosition());
                String fragment = currentBlock.substring(pos, endPosInBlock);
                result.append(fragment);

                long readLength = endPosInBlock - pos;
                remaining -= readLength;
                pos += readLength;
            }

            currentPosition += result.length();
            return result.toString();
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    /**
     * 尝试从当前位置尽量读取n个字符，实际读取不到n时返回已读部分，
     * 返回 "" 表示没有可读字符。
     */
    @Override
    public String tryRead(int n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException("tryRead length cannot be negative: " + n);
        }
        if (n == 0) return "";

        // 先看看缓存和底层/EOF的真实可用量
        long avail = Math.max(0, maxReadPosition - currentPosition);
        if (!eofReached && avail < n) {
            // 还未 EOF，尽力多加载些
            ensureDataReadTo(currentPosition + n);
            avail = Math.max(0, maxReadPosition - currentPosition);
        }
        int toRead = (int) Math.min(n, avail);
        if (toRead <= 0) return "";

        // 检查起始点在缓存窗口内
        if (!isPositionInCache(currentPosition)) {
            // 试着读一个块
            ensureDataReadTo(currentPosition + 1);
            if (!isPositionInCache(currentPosition)) {
                return ""; // 还是不行，直接返回
            }
        }

        StringBuilder result = new StringBuilder(toRead);
        long pos = currentPosition;
        int remain = toRead;

        while (remain > 0 && pos < maxReadPosition) {
            TextBlock block = findBlockForPosition(pos);
            if (block == null) break;
            long endPos = Math.min(pos + remain, block.getEndPosition());
            String frag = block.substring(pos, endPos);
            result.append(frag);
            int got = (int) (endPos - pos);
            remain -= got;
            pos += got;
        }

        int actual = result.length();
        currentPosition += actual;
        return result.toString();
    }

    @Override
    public int readChar() {
        try {
            // 确保当前位置有数据
            if (currentPosition >= maxReadPosition) {
                if (!eofReached) {
                    tryLoadMoreData();
                }
                if (currentPosition >= maxReadPosition) {
                    return -1;  // EOF
                }
            }

            // 检查当前位置是否在缓存窗口内
            if (!isPositionInCache(currentPosition)) {
                return -1;
            }

            TextBlock currentBlock = findBlockForPosition(currentPosition);
            if (currentBlock == null) {
                return -1;  // EOF
            }

            int relativeOffset = currentBlock.getRelativeOffset(currentPosition);
            String content = currentBlock.getContent();

            if (relativeOffset >= content.length()) {
                return -1;
            }

            char result = content.charAt(relativeOffset);
            currentPosition++;
            return result;
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public String readLine(int maxLength) {
        if (maxLength < 0) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA);
        }

        long avail = available();
        if (avail <= 0) {
            return "";
        }

        try {
            StringBuilder result = new StringBuilder();
            long pos = currentPosition;
            int remaining = (int) Math.min(maxLength, avail);

            while (remaining > 0 && pos < maxReadPosition) {
                // 确保有足够的数据
                if (pos >= maxReadPosition && !eofReached) {
                    tryLoadMoreData();
                }

                if (pos >= maxReadPosition) {
                    break;
                }

                // 检查位置是否在缓存内
                if (!isPositionInCache(pos)) {
                    // 尝试加载更多数据
                    ensureDataReadTo(pos + 1);
                    if (!isPositionInCache(pos)) {
                        break;
                    }
                }

                TextBlock currentBlock = findBlockForPosition(pos);
                if (currentBlock == null) {
                    break;
                }

                // 在当前块中查找换行符
                int newlineOffset = currentBlock.findNewlineFrom(pos, remaining);

                if (newlineOffset >= 0) {
                    // 找到换行符
                    long endPos = pos + newlineOffset;
                    String fragment = currentBlock.substring(pos, endPos);
                    result.append(fragment);

                    currentPosition = endPos + 1;

                    // 处理\r\n的情况
                    int relativeOffset = currentBlock.getRelativeOffset(endPos);
                    String content = currentBlock.getContent();
                    if (relativeOffset < content.length() && content.charAt(relativeOffset) == '\r') {
                        // 检查下一个字符是否是\n
                        if (endPos + 1 < maxReadPosition) {
                            if (endPos + 1 < currentBlock.getEndPosition()) {
                                if (content.charAt(relativeOffset + 1) == '\n') {
                                    currentPosition++;
                                }
                            } else {
                                // 需要检查下一个块
                                TextBlock nextBlock = findBlockForPosition(endPos + 1);
                                if (nextBlock != null && nextBlock.getContent().length() > 0 &&
                                        nextBlock.getContent().charAt(0) == '\n') {
                                    currentPosition++;
                                }
                            }
                        }
                    }

                    return result.toString();
                } else {
                    // 没有找到换行符，读取块中剩余的内容
                    long endPosInBlock = Math.min(pos + remaining, currentBlock.getEndPosition());
                    String fragment = currentBlock.substring(pos, endPosInBlock);
                    result.append(fragment);

                    long readLength = endPosInBlock - pos;
                    remaining -= readLength;
                    pos += readLength;
                }
            }

            currentPosition = pos;
            return result.toString();
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    @Override
    public long pos() {
        return currentPosition;
    }

    public void seek(long newPos) {
        if (newPos < 0) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA);
        }

        try {
            // 向前seek - 需要确保数据已被读取
            if (newPos > maxReadPosition) {
                ensureDataReadTo(newPos);
            }

            // 向后seek - 检查是否在缓存窗口内
            if (newPos < getFirstCachedPosition()) {
                throw new NopException(ERR_RECORD_NO_ENOUGH_DATA)
                        .param("reason", "Cannot seek backwards to position " + newPos +
                                ". Position is outside cache window [" + getFirstCachedPosition() +
                                ", " + getLastCachedPosition() + ")");
            }

            currentPosition = newPos;
        } catch (IOException e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    @Override
    public void reset() {
        clearCache();
        try {
            underlyingReader.reset();
        } catch (IOException e) {
            // 忽略重置异常
        }
        currentPosition = 0;
        maxReadPosition = 0;
        underlyingPosition = 0;
        eofReached = false;
    }

    @Override
    public ITextDataReader detach() {
        try {
            ITextDataReader detachedUnderlying = underlyingReader.detach();
            BlockCachedTextDataReader detached = new BlockCachedTextDataReader(
                    detachedUnderlying, defaultBlockSize, maxCacheBlocks,
                    maxSkipDistance, backwardCacheBlocks);
            detached.seek(currentPosition);
            return detached;
        } catch (Exception e) {
            throw new NopException(ERR_RECORD_NO_ENOUGH_DATA).cause(e);
        }
    }

    @Override
    public boolean isDetached() {
        return underlyingReader.isDetached();
    }

    @Override
    public void close() throws IOException {
        this.underlyingReader.close();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 确保数据已读取到指定位置
     */
    private void ensureDataReadTo(long targetPosition) throws IOException {
        while (maxReadPosition < targetPosition && !eofReached) {
            if (!tryLoadMoreData()) {
                break;
            }
        }

        if (targetPosition > maxReadPosition && eofReached) {
            throw new IOException("Cannot read to position " + targetPosition +
                    ", only " + maxReadPosition + " characters available");
        }
    }

    /**
     * 尝试加载更多数据，返回是否成功加载
     */
    private boolean tryLoadMoreData() throws IOException {
        if (eofReached) {
            return false;
        }

        TextBlock newBlock = loadNextBlock();
        if (newBlock == null) {
            eofReached = true;
            return false;
        }

        cachedBlocks.addLast(newBlock);
        maxReadPosition = newBlock.getEndPosition();

        // 限制缓存大小，移除旧的blocks
        while (cachedBlocks.size() > maxCacheBlocks) {
            TextBlock removedBlock = cachedBlocks.removeFirst();

            // 计算最小应保留的位置
            long minRetainPosition = Math.max(0,
                    currentPosition - (long) backwardCacheBlocks * defaultBlockSize);

            // 检查移除操作的安全性
            if (removedBlock.contains(currentPosition)) {
                throw new IOException("Cache window too small. Current position " +
                        currentPosition + " is in block being removed [" +
                        removedBlock.getStartPosition() + ", " + removedBlock.getEndPosition() +
                        "). Consider increasing maxCacheBlocks from " + maxCacheBlocks);
            }

            if (removedBlock.getEndPosition() > minRetainPosition) {
                throw new IOException("Cache window too small. Removing block [" +
                        removedBlock.getStartPosition() + ", " + removedBlock.getEndPosition() +
                        ") would break backward seek capability. Current position: " + currentPosition +
                        ", Min retain position: " + minRetainPosition +
                        ". Consider increasing maxCacheBlocks from " + maxCacheBlocks);
            }
        }

        return true;
    }

    /**
     * 检查指定位置是否在当前缓存范围内
     */
    protected boolean isPositionInCache(long position) {
        if (cachedBlocks.isEmpty()) {
            return false;
        }

        long firstPos = getFirstCachedPosition();
        long lastPos = getLastCachedPosition();
        return position >= firstPos && position < lastPos;
    }

    /**
     * 获取第一个缓存block的起始位置
     */
    private long getFirstCachedPosition() {
        return cachedBlocks.isEmpty() ? maxReadPosition : cachedBlocks.getFirst().getStartPosition();
    }

    /**
     * 获取最后一个缓存block的结束位置
     */
    private long getLastCachedPosition() {
        return cachedBlocks.isEmpty() ? maxReadPosition : cachedBlocks.getLast().getEndPosition();
    }

    /**
     * 查找包含指定位置的block
     */
    private TextBlock findBlockForPosition(long position) {
        for (TextBlock block : cachedBlocks) {
            if (block.contains(position)) {
                return block;
            }
        }
        return null;
    }

    /**
     * 从底层reader加载下一个block
     */
    protected TextBlock loadNextBlock() throws IOException {
        if (underlyingReader.isEof()) {
            return null;
        }

        // 尝试读取指定大小的文本块
        String content = underlyingReader.readFully(defaultBlockSize);

        if (content == null || content.isEmpty()) {
            return null;  // EOF
        }

        TextBlock block = new TextBlock(content, underlyingPosition);
        underlyingPosition += content.length();

        return block;
    }

    /**
     * 清空所有缓存的blocks
     */
    private void clearCache() {
        cachedBlocks.clear();
    }

    // ==================== 调试和监控方法 ====================

    /**
     * 获取缓存窗口信息，用于调试
     */
    public String getCacheWindowInfo() {
        if (cachedBlocks.isEmpty()) {
            return "Cache window: empty";
        }
        return String.format("Cache window: [%d, %d), blocks: %d, current pos: %d, max skip: %d",
                getFirstCachedPosition(), getLastCachedPosition(), cachedBlocks.size(),
                currentPosition, maxSkipDistance);
    }

    /**
     * 获取当前缓存统计信息
     */
    public CacheStats getCacheStats() {
        return new CacheStats(
                cachedBlocks.size(),
                maxCacheBlocks,
                cachedBlocks.isEmpty() ? 0 : getLastCachedPosition() - getFirstCachedPosition(),
                currentPosition,
                maxReadPosition,
                eofReached
        );
    }

    /**
     * 缓存统计信息类
     */
    public static class CacheStats {
        public final int currentBlocks;
        public final int maxBlocks;
        public final long cacheWindowSize;
        public final long currentPosition;
        public final long maxReadPosition;
        public final boolean eofReached;

        public CacheStats(int currentBlocks, int maxBlocks, long cacheWindowSize,
                          long currentPosition, long maxReadPosition, boolean eofReached) {
            this.currentBlocks = currentBlocks;
            this.maxBlocks = maxBlocks;
            this.cacheWindowSize = cacheWindowSize;
            this.currentPosition = currentPosition;
            this.maxReadPosition = maxReadPosition;
            this.eofReached = eofReached;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{blocks=%d/%d, window=%d chars, pos=%d, maxRead=%d, eof=%s}",
                    currentBlocks, maxBlocks, cacheWindowSize, currentPosition, maxReadPosition, eofReached);
        }
    }
}