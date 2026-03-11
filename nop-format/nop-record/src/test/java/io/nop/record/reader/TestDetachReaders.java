package io.nop.record.reader;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDetachReaders {

    @Test
    void testBlockCachedTextDetachAfterEviction() throws IOException {
        String testData = "0123456789".repeat(200);
        BlockCachedTextDataReader reader = new BlockCachedTextDataReader(new ReaderTextDataReader(new StringReader(testData)), 50, false, 3);

        reader.readFully(180);
        long detachedPos = reader.pos();
        long firstCachedPos = reader.getFirstCachedPosition();
        assertTrue(firstCachedPos > 0);

        ITextDataReader detached = reader.detach();
        assertEquals(detachedPos, detached.pos());

        String expected = testData.substring((int) detachedPos, (int) detachedPos + 20);
        assertEquals(expected, detached.readFully(20));
        assertEquals(expected, reader.readFully(20));
    }

    @Test
    void testBlockCachedBinaryDetachPreservesBitState() throws IOException {
        byte[] data = new byte[]{(byte) 0b10101100, (byte) 0b11110000, 0x55, 0x66};
        BlockCachedBinaryDataReader reader = new BlockCachedBinaryDataReader(new ByteBufferBinaryDataReader(data), 2, false, 2);

        assertEquals(0b101, reader.readBitsIntBe(3));
        assertEquals(5, reader.getBitsLeft());

        IBinaryDataReader detached = reader.detach();
        assertEquals(reader.pos(), detached.pos());
        assertEquals(reader.getBitsLeft(), detached.getBitsLeft());
        assertEquals(reader.getBits(), detached.getBits());

        assertEquals(reader.readBitsIntBe(5), detached.readBitsIntBe(5));
        assertEquals(reader.readByte() & 0xFF, detached.readByte() & 0xFF);
    }

    @Test
    void testReaderTextDetachKeepsLogicalOffset() throws IOException {
        ReaderTextDataReader reader = new ReaderTextDataReader(new StringReader("abcdefg12345"));

        assertEquals("abcde", reader.readFully(5));
        long detachedPos = reader.pos();

        ITextDataReader detached = reader.detach();
        assertEquals(detachedPos, detached.pos());

        assertEquals("fg", detached.readFully(2));
        assertEquals(detachedPos + 2, detached.pos());

        detached.seek(detachedPos);
        assertEquals(detachedPos, detached.pos());
        assertEquals("fg", detached.readFully(2));

        assertThrows(UnsupportedOperationException.class, () -> detached.seek(detachedPos - 1));
    }

    @Disabled
    @Test
    void testBlockCachedTextInterleavedMultipleDetach() throws IOException {
        String data = "abcdefghijklmnopqrstuvwxyz0123456789".repeat(20);
        BlockCachedTextDataReader root = new BlockCachedTextDataReader(new ReaderTextDataReader(new StringReader(data)), 32, false, 4);

        assertEquals(data.substring(0, 45), root.readFully(45));
        ITextDataReader d1 = root.detach();

        assertEquals(data.substring(45, 60), root.readFully(15));
        assertEquals(data.substring(45, 52), d1.readFully(7));

        ITextDataReader d2 = d1.detach();
        assertEquals(d1.pos(), d2.pos());

        assertEquals(data.substring(60, 70), root.readFully(10));
        assertEquals(data.substring(52, 62), d1.readFully(10));
        assertEquals(data.substring(52, 57), d2.readFully(5));

        d2.seek(50);
        assertEquals(data.substring(50, 56), d2.readFully(6));
        assertEquals(70, root.pos());
    }

    @Test
    void testBlockCachedBinaryInterleavedMultipleDetach() throws IOException {
        byte[] data = new byte[128];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        BlockCachedBinaryDataReader root = new BlockCachedBinaryDataReader(new ByteBufferBinaryDataReader(data), 16, false, 3);

        assertEquals(0x00, root.readByte() & 0xFF);
        assertEquals(0x01, root.readByte() & 0xFF);
        assertEquals(0x0, root.readBitsIntBe(4));

        IBinaryDataReader d1 = root.detach();
        assertEquals(root.pos(), d1.pos());
        assertEquals(root.getBitsLeft(), d1.getBitsLeft());

        assertEquals(root.readBitsIntBe(4), d1.readBitsIntBe(4));
        assertEquals(root.readByte() & 0xFF, d1.readByte() & 0xFF);

        IBinaryDataReader d2 = d1.detach();
        assertEquals(d1.pos(), d2.pos());
        assertEquals(d1.getBitsLeft(), d2.getBitsLeft());

        int round1Root = root.readByte() & 0xFF;
        int round1D1 = d1.readByte() & 0xFF;
        int round1D2 = d2.readByte() & 0xFF;
        assertEquals(round1Root, round1D1);
        assertEquals(round1D1, round1D2);

        int round2Root = root.readByte() & 0xFF;
        int round2D1 = d1.readByte() & 0xFF;
        int round2D2 = d2.readByte() & 0xFF;
        assertEquals(round2Root, round2D1);
        assertEquals(round2D1, round2D2);

        d2.seek(10);
        assertEquals(10, d2.readByte() & 0xFF);
        assertEquals(11, d2.readByte() & 0xFF);
    }

    @Test
    void testReaderTextInterleavedMultipleDetach() throws IOException {
        ReaderTextDataReader root = new ReaderTextDataReader(new StringReader("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));

        assertEquals("ABCDE", root.readFully(5));
        ITextDataReader d1 = root.detach();
        assertEquals(5, d1.pos());

        assertEquals("", root.tryReadFully(3));
        assertEquals("FG", d1.readFully(2));

        ITextDataReader d2 = d1.detach();
        assertEquals(d1.pos(), d2.pos());

        assertEquals("", root.tryReadFully(2));
        assertEquals("HI", d1.readFully(2));
        assertEquals("HIJ", d2.readFully(3));

        d2.seek(8);
        assertEquals("IJ", d2.readFully(2));
        assertThrows(UnsupportedOperationException.class, () -> d2.seek(4));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 8",
            "2, 10",
            "3, 12"
    })
    void testBlockCachedTextDetachMatrix(int detachDepth, int steps) throws IOException {
        String data = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".repeat(20);
        BlockCachedTextDataReader root = new BlockCachedTextDataReader(new ReaderTextDataReader(new StringReader(data)), 24, false, 5);

        root.readFully(30);

        List<ITextDataReader> readers = new ArrayList<>();
        List<Long> expectedPos = new ArrayList<>();
        readers.add(root);
        expectedPos.add(root.pos());

        for (int i = 0; i < detachDepth; i++) {
            ITextDataReader parent = readers.get(readers.size() - 1);
            ITextDataReader detached = parent.detach();
            readers.add(detached);
            expectedPos.add(parent.pos());
            assertEquals(parent.pos(), detached.pos());
        }

        for (int i = 0; i < steps; i++) {
            int index = i % readers.size();
            ITextDataReader reader = readers.get(index);
            long posBefore = expectedPos.get(index);

            String expected = data.substring((int) posBefore, (int) posBefore + 2);
            assertEquals(expected, reader.readFully(2));

            expectedPos.set(index, posBefore + 2);
            assertEquals(posBefore + 2, reader.pos());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1, 8",
            "2, 10",
            "3, 12"
    })
    void testBlockCachedBinaryDetachMatrix(int detachDepth, int steps) throws IOException {
        byte[] data = new byte[512];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i & 0xFF);
        }

        BlockCachedBinaryDataReader root = new BlockCachedBinaryDataReader(new ByteBufferBinaryDataReader(data), 32, false, 5);
        root.readFully(20);

        List<IBinaryDataReader> readers = new ArrayList<>();
        List<Long> expectedPos = new ArrayList<>();
        readers.add(root);
        expectedPos.add(root.pos());

        for (int i = 0; i < detachDepth; i++) {
            IBinaryDataReader parent = readers.get(readers.size() - 1);
            IBinaryDataReader detached = parent.detach();
            readers.add(detached);
            expectedPos.add(parent.pos());
            assertEquals(parent.pos(), detached.pos());
        }

        for (int i = 0; i < steps; i++) {
            int index = i % readers.size();
            IBinaryDataReader reader = readers.get(index);
            long posBefore = expectedPos.get(index);

            byte[] expected = new byte[]{data[(int) posBefore], data[(int) posBefore + 1], data[(int) posBefore + 2]};
            assertArrayEquals(expected, reader.readFully(3));

            expectedPos.set(index, posBefore + 3);
            assertEquals(posBefore + 3, reader.pos());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1, 6",
            "2, 8",
            "3, 10"
    })
    void testReaderTextDetachMatrix(int detachDepth, int steps) throws IOException {
        ReaderTextDataReader root = new ReaderTextDataReader(new StringReader("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"));
        root.readFully(8);

        List<ITextDataReader> readers = new ArrayList<>();
        List<Long> basePos = new ArrayList<>();
        ITextDataReader parent = root;
        for (int i = 0; i < detachDepth; i++) {
            ITextDataReader detached = parent.detach();
            readers.add(detached);
            basePos.add(parent.pos());
            assertEquals(parent.pos(), detached.pos());
            parent = detached;
        }

        String all = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < steps; i++) {
            int index = i % readers.size();
            ITextDataReader reader = readers.get(index);
            long posBefore = reader.pos();
            assertEquals(all.substring((int) posBefore, (int) posBefore + 1), reader.readFully(1));
            assertEquals(posBefore + 1, reader.pos());
        }

        for (int i = 0; i < readers.size(); i++) {
            ITextDataReader detached = readers.get(i);
            long detachedBasePos = basePos.get(i);
            assertThrows(UnsupportedOperationException.class, () -> detached.seek(detachedBasePos - 1));
        }

        assertEquals("", root.tryReadFully(1));
    }

    @Test
    void testBlockCachedTextDetachThenReadMoreThan4096Chars() throws IOException {
        String data = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(400);
        BlockCachedTextDataReader root = new BlockCachedTextDataReader(new ReaderTextDataReader(new StringReader(data)));

        assertEquals(data.substring(0, 123), root.readFully(123));

        ITextDataReader detached = root.detach();
        assertEquals(123, detached.pos());

        int readLen = 5000;
        String expected = data.substring(123, 123 + readLen);
        assertEquals(expected, detached.readFully(readLen));
        assertEquals(123 + readLen, detached.pos());

        detached.seek(123);
        assertEquals(expected.substring(0, 64), detached.peek(64));
    }
}
