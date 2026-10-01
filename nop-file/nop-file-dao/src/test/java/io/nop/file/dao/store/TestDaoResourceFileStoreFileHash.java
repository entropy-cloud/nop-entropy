package io.nop.file.dao.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.file.core.UploadRequestBean;
import io.nop.file.dao.entity.NopFileRecord;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FILE_HASH 落库回归测试（plan 2282 [G9-04-01]）。
 * <p>
 * 契约：saveFile 的两条内容通路（已知长度直写 / 未知长度经临时资源暂存）
 * 都必须在入库记录上写入内容 SHA-256 摘要——
 * 同内容 hash 相同、内容变化 hash 随之变化、hash 始终非空。
 * 本测试只锁定"摘要落库可查询"，不涉及任何去重复用语义。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestDaoResourceFileStoreFileHash extends JunitAutoTestCase {

    private static final byte[] CONTENT_A = "nop-file-hash-content-A".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTENT_B = "nop-file-hash-content-B".getBytes(StandardCharsets.UTF_8);

    @Inject
    DaoResourceFileStore fileStore;

    @Inject
    IDaoProvider daoProvider;

    static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available in the JDK", e);
        }
    }

    private UploadRequestBean upload(byte[] content, long declaredLength) {
        UploadRequestBean req = new UploadRequestBean(
                new ByteArrayInputStream(content), "a.txt", declaredLength, "text/plain");
        req.setBizObjName("TestHashObj");
        req.setFieldName("fileField");
        return req;
    }

    private NopFileRecord requireRecord(String fileId) {
        return daoProvider.daoFor(NopFileRecord.class).requireEntityById(fileId);
    }

    @Test
    void testSaveFileKnownLengthWritesFileHash() {
        String fileId = fileStore.saveFile(upload(CONTENT_A, CONTENT_A.length), 10_000_000L);

        NopFileRecord record = requireRecord(fileId);
        assertNotNull(record.getFileHash(), "FILE_HASH must be persisted on the regular upload path");
        assertEquals(sha256Hex(CONTENT_A), record.getFileHash());
        assertTrue(record.getFileHash().matches("[0-9a-f]{64}"), "hash must be 64 lowercase hex chars");
    }

    @Test
    void testSaveFileUnknownLengthWritesFileHash() {
        // declaredLength <= 0：内容先经临时资源暂存，再落入存储目录
        String fileId = fileStore.saveFile(upload(CONTENT_A, 0), 10_000_000L);

        NopFileRecord record = requireRecord(fileId);
        assertNotNull(record.getFileHash(), "FILE_HASH must be persisted on the temp-resource path");
        assertEquals(sha256Hex(CONTENT_A), record.getFileHash());
    }

    @Test
    void testSaveFileSameContentSameHash() {
        String fileId1 = fileStore.saveFile(upload(CONTENT_A, CONTENT_A.length), 10_000_000L);
        String fileId2 = fileStore.saveFile(upload(CONTENT_A, CONTENT_A.length), 10_000_000L);

        assertNotEquals(fileId1, fileId2);
        String hash1 = requireRecord(fileId1).getFileHash();
        String hash2 = requireRecord(fileId2).getFileHash();
        assertNotNull(hash1);
        assertEquals(hash1, hash2, "identical content must produce identical hash");
    }

    @Test
    void testSaveFileChangedContentChangesHash() {
        String fileIdA = fileStore.saveFile(upload(CONTENT_A, CONTENT_A.length), 10_000_000L);
        String fileIdB = fileStore.saveFile(upload(CONTENT_B, CONTENT_B.length), 10_000_000L);

        String hashA = requireRecord(fileIdA).getFileHash();
        String hashB = requireRecord(fileIdB).getFileHash();
        assertNotNull(hashA);
        assertNotNull(hashB);
        assertNotEquals(hashA, hashB, "changed content must change the persisted hash");
    }
}
