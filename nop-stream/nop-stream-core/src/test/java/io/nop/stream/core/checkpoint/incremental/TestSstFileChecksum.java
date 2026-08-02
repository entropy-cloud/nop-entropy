/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.incremental;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSstFileChecksum {

    @TempDir
    Path tmp;

    // Known SHA-256 of "hello\n"
    private static final String HELLO_SHA256 =
            "5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03";

    @Test
    void sha256OfFileMatchesKnownDigest() throws Exception {
        Path f = tmp.resolve("a.sst");
        Files.write(f, "hello\n".getBytes());

        String digest = SstFileChecksum.sha256Hex(f);
        assertEquals(HELLO_SHA256, digest);
        assertEquals(64, digest.length());
    }

    @Test
    void identicalContentProducesIdenticalDigestRegardlessOfName() throws Exception {
        Path f1 = tmp.resolve("one.sst");
        Path f2 = tmp.resolve("two.sst");
        byte[] data = "the quick brown fox".getBytes();
        Files.write(f1, data);
        Files.write(f2, data);

        assertEquals(SstFileChecksum.sha256Hex(f1), SstFileChecksum.sha256Hex(f2));
    }

    @Test
    void differentContentProducesDifferentDigest() throws Exception {
        Path f1 = tmp.resolve("a.sst");
        Path f2 = tmp.resolve("b.sst");
        Files.write(f1, "content-a".getBytes());
        Files.write(f2, "content-b".getBytes());

        assertNotEquals(SstFileChecksum.sha256Hex(f1), SstFileChecksum.sha256Hex(f2));
    }

    @Test
    void sha256OfByteArrayMatchesFile() throws Exception {
        Path f = tmp.resolve("a.sst");
        byte[] data = "the quick brown fox".getBytes();
        Files.write(f, data);

        assertEquals(SstFileChecksum.sha256Hex(data), SstFileChecksum.sha256Hex(f));
    }

    @Test
    void sha256OfNullByteArrayThrows() {
        assertThrows(IllegalArgumentException.class, () -> SstFileChecksum.sha256Hex((byte[]) null));
    }

    @Test
    void digestIsLowercaseHex() throws Exception {
        Path f = tmp.resolve("a.sst");
        Files.write(f, "x".getBytes());
        String digest = SstFileChecksum.sha256Hex(f);
        for (int i = 0; i < digest.length(); i++) {
            char c = digest.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            assert ok : "non-hex char: " + c;
        }
    }
}
