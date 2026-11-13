package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record.RecordConstants;
import io.nop.record.RecordErrors;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.reader.IBinaryDataReader;
import io.nop.record.reader.RandomAccessFileBinaryDataReader;
import io.nop.record.reader.StreamBinaryDataReader;
import io.nop.record.resource.ModelBasedBinaryRecordInput;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 5.4-C5-001：数据驱动 lengthExpr 的字段/对象长度必须在任何 reader 分配之前受上界约束。
 * 恶意长度断言 ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG（而非分配后 EOF 或 OOM）为修复准入门槛。
 */
public class TestRecordDecodeBounds_C5_001 extends BaseTestCase {
    static final String FIELD_MODEL = "/test/record/test-length-expr-bound.record-file.xml";
    static final String OBJECT_MODEL = "/test/record/test-length-expr-object-bound.record-file.xml";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    RecordFileMeta meta(String path) {
        return (RecordFileMeta) new DslModelParser().parseFromVirtualPath(path);
    }

    NopException decodeFails(String modelPath, IBinaryDataReader in) {
        RecordFileMeta fileMeta = meta(modelPath);
        return assertThrows(NopException.class, () -> {
            ModelBasedBinaryRecordInput<Map<String, Object>> input = new ModelBasedBinaryRecordInput<>(in, fileMeta);
            try {
                input.next();
            } finally {
                input.close();
            }
        });
    }

    void assertLengthRejected(NopException e, long length) {
        assertEquals(RecordErrors.ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG.getErrorCode(), e.getErrorCode());
        assertEquals(length, ((Number) e.getParam(RecordErrors.ARG_LENGTH)).longValue());
        assertEquals(RecordConstants.DEFAULT_MAX_FIELD_LENGTH,
                ((Number) e.getParam(RecordErrors.ARG_MAX_LENGTH)).intValue());
    }

    static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void testMaliciousFieldLengthRejectedWithEagerReader() {
        NopException e = decodeFails(FIELD_MODEL, new ByteBufferBinaryDataReader(bytes("0134217728abc")));
        assertLengthRejected(e, 134217728L);
    }

    // 修复准入门槛：0x7FFFFF00 长度在分配前被拒绝
    @Test
    public void testAdmissionThresholdHugeLengthRejectedBeforeAllocation() {
        NopException e = decodeFails(FIELD_MODEL, new ByteBufferBinaryDataReader(bytes("2147483392xyz")));
        assertLengthRejected(e, 0x7FFFFF00L);
    }

    @Test
    public void testMaliciousFieldLengthRejectedWithLazyStreamReader() {
        NopException e = decodeFails(FIELD_MODEL,
                new StreamBinaryDataReader(new ByteArrayInputStream(bytes("0134217728abc"))));
        assertLengthRejected(e, 134217728L);
    }

    // 文件路径端到端：RandomAccessFile reader 即 ModelBasedResourceRecordIO 文件分支所用 reader
    @Test
    public void testMaliciousFieldLengthRejectedOnFilePath() throws Exception {
        File file = File.createTempFile("c5-001-", ".bin");
        try {
            Files.write(file.toPath(), bytes("0134217728abc"));
            RandomAccessFileBinaryDataReader in = new RandomAccessFileBinaryDataReader(file);
            NopException e = decodeFails(FIELD_MODEL, in);
            assertLengthRejected(e, 134217728L);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testMaliciousObjectLengthRejected() {
        NopException e = decodeFails(OBJECT_MODEL, new ByteBufferBinaryDataReader(bytes("aaaaa")));
        assertLengthRejected(e, 134217728L);
    }

    // 对象级 lengthExpr 未声明静态 length 时，缺省值为 -1 而非对 null 拆箱
    @Test
    public void testLegalObjectLengthExprWithoutStaticLength() throws Exception {
        RecordFileMeta fileMeta = meta("/test/record/test-length-expr-object-legal.record-file.xml");
        ModelBasedBinaryRecordInput<Map<String, Object>> input = new ModelBasedBinaryRecordInput<>(
                new ByteBufferBinaryDataReader(bytes("abcde")), fileMeta);
        Map<String, Object> record = input.next();
        assertEquals("abcde", record.get("a"));
        input.close();
    }

    @Test
    public void testLegalLengthDecodes() throws Exception {
        RecordFileMeta fileMeta = meta(FIELD_MODEL);
        ModelBasedBinaryRecordInput<Map<String, Object>> input = new ModelBasedBinaryRecordInput<>(
                new ByteBufferBinaryDataReader(bytes("0000000005hello")), fileMeta);
        Map<String, Object> record = input.next();
        assertEquals("hello", record.get("data"));
        input.close();
    }

    // 上界为严格大于：等于上界的长度不被上界拒绝（此处因数据不足落到 EOF 错误）
    @Test
    public void testLengthEqualToMaxIsNotRejectedByBound() {
        String len = String.format("%010d", RecordConstants.DEFAULT_MAX_FIELD_LENGTH);
        NopException e = decodeFails(FIELD_MODEL, new ByteBufferBinaryDataReader(bytes(len + "abc")));
        assertEquals(RecordErrors.ERR_RECORD_NO_ENOUGH_DATA.getErrorCode(), e.getErrorCode());
    }
}
