package io.nop.file.core.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 分片上传 API 的空实现不得以 null 静默返回（下游 NPE 难定位），
 * 未实现的能力必须显式抛 UnsupportedOperationException。
 */
class TestChunkFileUploadHandler {

    private final ChunkFileUploadHandler handler = new ChunkFileUploadHandler();

    @Test
    void startChunkApi_notImplemented_throwsExplicitly() {
        assertThrows(UnsupportedOperationException.class, () -> handler.startChunkApi(null, null));
    }

    @Test
    void chunkApi_notImplemented_throwsExplicitly() {
        assertThrows(UnsupportedOperationException.class, () -> handler.chunkApi(null, null));
    }

    @Test
    void finishChunkApi_notImplemented_throwsExplicitly() {
        assertThrows(UnsupportedOperationException.class, () -> handler.finishChunkApi(null, null));
    }
}
