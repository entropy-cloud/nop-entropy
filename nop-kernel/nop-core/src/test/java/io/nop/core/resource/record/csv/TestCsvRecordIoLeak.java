/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.record.csv;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.resource.impl.DelegateResource;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCsvRecordIoLeak {

    static class FailingReader extends Reader {
        final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public int read(char[] cbuf, int off, int len) throws java.io.IOException {
            throw new java.io.IOException("mock-read-fail");
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    static class RecordingWriter extends Writer {
        final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void write(char[] cbuf, int off, int len) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    /**
     * 构造函数中CSV解析失败时，已打开的reader必须被关闭，否则调用方无法关闭、句柄泄漏
     */
    @Test
    public void testInputClosesReaderOnParseError() {
        FailingReader reader = new FailingReader();
        DelegateResource resource = new DelegateResource("/test/a.csv",
                new ByteArrayResource("/test/a.csv", new byte[0], 0L)) {
            @Override
            public Reader getReader(String encoding) {
                return reader;
            }
        };

        assertThrows(NopException.class,
                () -> new CsvRecordInput<>(resource, "UTF-8", CSVFormat.DEFAULT, null, false, false));
        assertTrue(reader.closed.get(), "reader should be closed when CsvRecordInput constructor fails");
    }

    /**
     * 构造函数中CSVPrinter初始化失败时，已打开的writer必须被关闭
     */
    @Test
    public void testOutputClosesWriterOnPrinterError() {
        RecordingWriter writer = new RecordingWriter();
        DelegateResource resource = new DelegateResource("/test/a.csv",
                new ByteArrayResource("/test/a.csv", new byte[0], 0L)) {
            @Override
            public Writer getWriter(String encoding) {
                return writer;
            }
        };

        // null format导致CSVPrinter构造失败；commons-csv抛出的运行时异常经NopException.adapt原样重抛
        assertThrows(RuntimeException.class,
                () -> new CsvRecordOutput<>(resource, "UTF-8", null, false));
        assertTrue(writer.closed.get(), "writer should be closed when CsvRecordOutput constructor fails");
    }
}
