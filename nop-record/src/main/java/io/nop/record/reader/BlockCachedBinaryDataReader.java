package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import static io.nop.record.RecordErrors.ARG_FIRST_CACHED_POS;
import static io.nop.record.RecordErrors.ARG_POS;
import static io.nop.record.RecordErrors.ERR_RECORD_POS_NOT_IN_CACHE;

/**
 * 基于块链表的缓存二进制数据读取器
 * 维护一个滚动窗口，所有数据必须先读取到缓存中才能访问
 * 只支持在缓存窗口范围内的向后seek，超出范围直接报错
 */
public class BlockCachedBinaryDataReader implements IBinaryDataReader {

    // 默认配置常量
    private static final int DEFAULT_BUFFER_SIZE = 4096;
    private static final long DEFAULT_MAX_SKIP_DISTANCE = 1024 * 1024; // 1MB
    private static final int DEFAULT_MAX_CACHE_BLOCKS = 8;
    private static final int DEFAULT_BACKWARD_CACHE_BLOCKS = 2; // 保留的向后缓存块数量

    /**
     * 数据块类，包装ByteBuffer和相关元数据
     */
    protected static class DataBlock {
        private final ByteBuffer buffer;
        private final long startPosition;  // 该block在整个流中的起始位置
        private final int size;            // 实际数据大小

        public DataBlock(ByteBuffer buffer, long startPosition, int size) {
            this.buffer = buffer;
            this.startPosition = startPosition;
            this.size = size;
        }

        public ByteBuffer getBuffer() {
            return buffer;
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
    }

    private final IBinaryDataReader underlyingReader;
    private final int defaultBlockSize;
    private final boolean strictBlockSize;  // 是否要求严格的block大小
    private final int maxCacheBlocks;       // 最大缓存block数量
    private final long maxSkipDistance;     // 最大允许的skip距离
    private final int backwardCacheBlocks;  // 保留的向后缓存块数量

    private final LinkedList<DataBlock> cachedBlocks = new LinkedList<>();
    private long currentPosition = 0;       // 当前在整个流中的位置
    private long maxReadPosition = 0;       // 已读取到的最大位置
    private long underlyingPosition = 0;    // 底层reader的当前位置
    private boolean eofReached = false;     // 是否已到达文件末尾

    // bit操作相关状态
    private int bitsLeft = 0;
    private long bits = 0;

    /**
     * 完整构造函数
     *
     * @param underlyingReader    底层数据读取器
     * @param defaultBlockSize    默认块大小
     * @param strictBlockSize     是否要求严格的块大小（除了最后一个块）
     * @param maxCacheBlocks      最大缓存块数量，超过时会释放旧块
     * @param maxSkipDistance     最大允许的skip距离
     * @param backwardCacheBlocks 保留的向后缓存块数量
     */
    public BlockCachedBinaryDataReader(IBinaryDataReader underlyingReader,
                                       int defaultBlockSize,
                                       boolean strictBlockSize,
                                       int maxCacheBlocks,
                                       long maxSkipDistance,
                                       int backwardCacheBlocks) {
        this.underlyingReader = underlyingReader;
        this.defaultBlockSize = defaultBlockSize;
        this.strictBlockSize = strictBlockSize;
        this.maxCacheBlocks = maxCacheBlocks;
        this.maxSkipDistance = maxSkipDistance;
        this.backwardCacheBlocks = backwardCacheBlocks;
    }

    /**
     * 便利构造函数
     */
    public BlockCachedBinaryDataReader(IBinaryDataReader underlyingReader,
                                       int defaultBlockSize,
                                       boolean strictBlockSize,
                                       int maxCacheBlocks) {
        this(underlyingReader, defaultBlockSize, strictBlockSize, maxCacheBlocks,
                DEFAULT_MAX_SKIP_DISTANCE, DEFAULT_BACKWARD_CACHE_BLOCKS);
    }

    /**
     * 默认配置构造函数
     */
    public BlockCachedBinaryDataReader(IBinaryDataReader underlyingReader) {
        this(underlyingReader, DEFAULT_BUFFER_SIZE, false, DEFAULT_MAX_CACHE_BLOCKS,
                DEFAULT_MAX_SKIP_DISTANCE, DEFAULT_BACKWARD_CACHE_BLOCKS);
    }

    @Override
    public boolean isEof() throws IOException {
        // 如果当前位置已经到达最大读取位置，尝试读取更多数据
        if (currentPosition >= maxReadPosition && !eofReached) {
            tryLoadMoreData();
        }
        return eofReached && currentPosition >= maxReadPosition;
    }

    @Override
    public boolean hasRemainingBytes() throws IOException {
        return !isEof();
    }

    @Override
    public long pos() {
        return currentPosition;
    }

    @Override
    public void seek(long newPos) throws IOException {
        if (newPos < 0) {
            throw new IllegalArgumentException("Position cannot be negative: " + newPos);
        }

        // 向前seek - 需要确保数据已被读取
        if (newPos > maxReadPosition) {
            ensureDataReadTo(newPos);
        }

        // 向后seek - 检查是否在缓存窗口内
        if (newPos < getFirstCachedPosition()) {
            throw new NopException(ERR_RECORD_POS_NOT_IN_CACHE)
                    .param(ARG_POS, newPos)
                    .param(ARG_FIRST_CACHED_POS, getFirstCachedPosition());
        }


        currentPosition = newPos;
    }

    @Override
    public void skip(long n) throws IOException {
        if (n <= 0) return;

        // 检查skip距离是否合理
        if (n > maxSkipDistance) {
            throw new IOException("Skip distance " + n + " exceeds maximum allowed " + maxSkipDistance +
                    ". This prevents excessive memory usage during large skips.");
        }

        long targetPos = currentPosition + n;

        // 确保目标位置的数据已经被读取到缓存中
        if (targetPos > maxReadPosition) {
            ensureDataReadTo(targetPos);
        }

        currentPosition = targetPos;
    }

    @Override
    public int read() throws IOException {
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
            throw new IOException("Current position " + currentPosition +
                    " is outside cache window [" + getFirstCachedPosition() +
                    ", " + getLastCachedPosition() + ")");
        }

        DataBlock currentBlock = findBlockForPosition(currentPosition);
        if (currentBlock == null) {
            return -1;  // EOF
        }

        int relativeOffset = currentBlock.getRelativeOffset(currentPosition);
        ByteBuffer buffer = currentBlock.getBuffer();

        // 保存原始position
        int originalPos = buffer.position();
        buffer.position(relativeOffset);

        int result = buffer.hasRemaining() ? (buffer.get() & 0xFF) : -1;

        // 恢复原始position
        buffer.position(originalPos);

        if (result >= 0) {
            currentPosition++;
        }

        return result;
    }

    @Override
    public int read(byte[] data, int offset, int len) throws IOException {
        if (data == null) {
            throw new NullPointerException();
        }
        if (offset < 0 || len < 0 || len > data.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }

        // 确保目标范围的数据已被读取
        long targetPos = currentPosition + len;
        if (targetPos > maxReadPosition) {
            ensureDataReadTo(targetPos);
        }

        int totalRead = 0;
        int remaining = len;

        while (remaining > 0 && currentPosition < maxReadPosition) {
            // 检查当前位置是否在缓存窗口内
            if (!isPositionInCache(currentPosition)) {
                throw new IOException("Current position " + currentPosition +
                        " is outside cache window [" + getFirstCachedPosition() +
                        ", " + getLastCachedPosition() + ")");
            }

            DataBlock currentBlock = findBlockForPosition(currentPosition);
            if (currentBlock == null) {
                break;  // EOF
            }

            int relativeOffset = currentBlock.getRelativeOffset(currentPosition);
            ByteBuffer buffer = currentBlock.getBuffer();

            // 计算在当前block中可以读取的字节数
            int availableInBlock = currentBlock.getSize() - relativeOffset;
            int toReadFromBlock = Math.min(remaining, availableInBlock);

            // 保存原始position
            int originalPos = buffer.position();
            buffer.position(relativeOffset);

            // 读取数据
            buffer.get(data, offset + totalRead, toReadFromBlock);

            // 恢复原始position
            buffer.position(originalPos);

            totalRead += toReadFromBlock;
            remaining -= toReadFromBlock;
            currentPosition += toReadFromBlock;
        }

        return totalRead > 0 ? totalRead : -1;
    }

    @Override
    public long available() throws IOException {
        long cachedRemaining = maxReadPosition - currentPosition;
        if (eofReached) {
            return Math.max(0, cachedRemaining);
        }
        return cachedRemaining + underlyingReader.available();
    }

    @Override
    public void reset() throws IOException {
        clearCache();
        underlyingReader.reset();
        currentPosition = 0;
        maxReadPosition = 0;
        underlyingPosition = 0;
        eofReached = false;
        bitsLeft = 0;
        bits = 0;
    }

    @Override
    public void alignToByte() {
        bitsLeft = 0;
        bits = 0;
    }

    @Override
    public int getBitsLeft() {
        return bitsLeft;
    }

    @Override
    public void setBitsLeft(int bitsLeft) {
        this.bitsLeft = bitsLeft;
    }

    @Override
    public long getBits() {
        return bits;
    }

    @Override
    public void setBits(long bits) {
        this.bits = bits;
    }

    @Override
    public IBinaryDataReader subInput(long maxLength) throws IOException {
        return new SubBinaryDataReader(this, maxLength);
    }

    @Override
    public IBinaryDataReader detach() throws IOException {
        IBinaryDataReader detachedUnderlying = underlyingReader.detach();
        BlockCachedBinaryDataReader detached = new BlockCachedBinaryDataReader(
                detachedUnderlying, defaultBlockSize, strictBlockSize, maxCacheBlocks,
                maxSkipDistance, backwardCacheBlocks);
        detached.seek(currentPosition);
        return detached;
    }

    @Override
    public IBinaryDataReader duplicate() throws IOException {
        BlockCachedBinaryDataReader duplicate = new BlockCachedBinaryDataReader(
                underlyingReader, defaultBlockSize, strictBlockSize, maxCacheBlocks,
                maxSkipDistance, backwardCacheBlocks);
        duplicate.seek(currentPosition);
        return duplicate;
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
            throw new EOFException("Cannot read to position " + targetPosition +
                    ", only " + maxReadPosition + " bytes available");
        }
    }

    /**
     * 尝试加载更多数据，返回是否成功加载
     */
    private boolean tryLoadMoreData() throws IOException {
        if (eofReached) {
            return false;
        }

        DataBlock newBlock = loadNextBlock();
        if (newBlock == null) {
            eofReached = true;
            return false;
        }

        cachedBlocks.addLast(newBlock);
        maxReadPosition = newBlock.getEndPosition();

        // 限制缓存大小，移除旧的blocks
        // 改进的缓存管理策略：确保保留足够的向后缓存空间
        while (cachedBlocks.size() > maxCacheBlocks) {
            cachedBlocks.removeFirst();
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
    protected long getFirstCachedPosition() {
        return cachedBlocks.isEmpty() ? maxReadPosition : cachedBlocks.getFirst().getStartPosition();
    }

    /**
     * 获取最后一个缓存block的结束位置
     */
    protected long getLastCachedPosition() {
        return cachedBlocks.isEmpty() ? maxReadPosition : cachedBlocks.getLast().getEndPosition();
    }

    /**
     * 查找包含指定位置的block
     */
    private DataBlock findBlockForPosition(long position) {
        for (DataBlock block : cachedBlocks) {
            if (block.contains(position)) {
                return block;
            }
        }
        return null;
    }

    /**
     * 从底层reader加载下一个block
     */
    protected DataBlock loadNextBlock() throws IOException {
        if (underlyingReader.isEof()) {
            return null;
        }

        byte[] buffer = new byte[defaultBlockSize];
        int bytesRead = underlyingReader.read(buffer, 0, defaultBlockSize);

        if (bytesRead <= 0) {
            return null;  // EOF
        }

        // 严格块大小检查
        if (strictBlockSize && bytesRead < defaultBlockSize) {
            if (underlyingReader.hasRemainingBytes()) {
                throw new IOException("Expected " + defaultBlockSize + " bytes but read " + bytesRead);
            }
        }

        return new DataBlock(ByteBuffer.wrap(buffer, 0, bytesRead),
                underlyingPosition, bytesRead);
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
            return String.format("CacheStats{blocks=%d/%d, window=%d bytes, pos=%d, maxRead=%d, eof=%s}",
                    currentBlocks, maxBlocks, cacheWindowSize, currentPosition, maxReadPosition, eofReached);
        }
    }
}