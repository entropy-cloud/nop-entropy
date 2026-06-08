package io.nop.ai.shell.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class ShellIOTest {

    @Test
    void testShellChunkText() {
        ShellChunk chunk = ShellChunk.text("hello");
        assertTrue(chunk.isText());
        assertFalse(chunk.isBinary());
        assertFalse(chunk.isEof());
        assertEquals("hello", chunk.asText());
    }

    @Test
    void testShellChunkBinary() {
        ShellChunk chunk = ShellChunk.binary(new byte[]{1, 2, 3});
        assertFalse(chunk.isText());
        assertTrue(chunk.isBinary());
        assertFalse(chunk.isEof());
    }

    @Test
    void testShellChunkEof() {
        ShellChunk chunk = ShellChunk.eof();
        assertFalse(chunk.isText());
        assertFalse(chunk.isBinary());
        assertTrue(chunk.isEof());
    }

    @Test
    void testShellChunkTextNullSafety() {
        ShellChunk chunk = ShellChunk.text(null);
        assertTrue(chunk.isText());
        assertEquals("", chunk.asText());
    }

    @Test
    void testReadLineWithPrintNoNewline() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.print("hello");
        output.close();

        IShellInput input = output.asInput();
        assertTrue(input instanceof AbstractShellInput);
        String line = ((AbstractShellInput) input).readLine();
        assertEquals("hello", line);
        assertNull(((AbstractShellInput) input).readLine());
    }

    @Test
    void testReadLineConsumesMultipleChunksBeforeNewline() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.print("hel");
        output.print("lo\n");
        output.close();

        IShellInput input = output.asInput();
        String line = ((AbstractShellInput) input).readLine();
        assertEquals("hello", line);
        assertNull(((AbstractShellInput) input).readLine());
    }

    @Test
    void testReadLineMultipleLines() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.println("line1");
        output.println("line2");
        output.println("line3");
        output.close();

        AbstractShellInput input = (AbstractShellInput) output.asInput();
        assertEquals("line1", input.readLine());
        assertEquals("line2", input.readLine());
        assertEquals("line3", input.readLine());
        assertNull(input.readLine());
        assertNull(input.readLine());
    }

    @Test
    void testReadLineEofWithLeftoverBuffer() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.print("partial");
        output.close();

        AbstractShellInput input = (AbstractShellInput) output.asInput();
        assertEquals("partial", input.readLine());
        assertNull(input.readLine());
    }

    @Test
    void testReadAllText() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.print("hello ");
        output.println("world");
        output.close();

        AbstractShellInput input = (AbstractShellInput) output.asInput();
        assertEquals("hello world\n", input.readAllText());
    }

    @Test
    void testReadAllTextEmpty() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.close();

        AbstractShellInput input = (AbstractShellInput) output.asInput();
        assertEquals("", input.readAllText());
    }

    @Test
    void testBlockingQueueShellOutputWriteAfterCloseThrows() {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.close();
        assertThrows(IllegalStateException.class, () -> output.write(ShellChunk.text("x")));
    }

    @Test
    void testBlockingQueueShellOutputDoubleCloseIdempotent() {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.close();
        output.close();
    }

    @Test
    void testBlockingQueueShellOutputAsInputReadReturnsNullAfterEof() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.println("data");
        output.close();

        IShellInput input = output.asInput();
        ShellChunk chunk1 = input.read();
        assertNotNull(chunk1);
        assertTrue(chunk1.isText());

        ShellChunk eof = input.read();
        assertNull(eof);

        ShellChunk again = input.read();
        assertNull(again);
    }

    @Test
    void testBlockingQueueShellOutputBoundedCapacityWriteThrowsAfterClose() {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput(1);
        output.write(ShellChunk.text("a"));
        output.close();
        assertThrows(IllegalStateException.class, () -> output.write(ShellChunk.text("b")));
    }

    @Test
    void testListShellOutputWriteAndClose() {
        ListShellOutput output = new ListShellOutput();
        output.println("line1");
        output.println("line2");
        output.close();

        List<ShellChunk> chunks = output.getChunks();
        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).isText());
        assertTrue(chunks.get(1).isText());
        assertTrue(chunks.get(2).isEof());
    }

    @Test
    void testListShellOutputGetTextContent() {
        ListShellOutput output = new ListShellOutput();
        output.print("hello ");
        output.println("world");
        assertEquals("hello world\n", output.getTextContent());
    }

    @Test
    void testListShellOutputWriteAfterCloseThrows() {
        ListShellOutput output = new ListShellOutput();
        output.close();
        assertThrows(IllegalStateException.class, () -> output.write(ShellChunk.text("x")));
    }

    @Test
    void testListShellOutputAsInput() {
        ListShellOutput output = new ListShellOutput();
        output.println("line1");
        output.close();

        AbstractShellInput input = (AbstractShellInput) output.asInput();
        assertEquals("line1", input.readLine());
        assertNull(input.readLine());
    }

    @Test
    void testBlockingQueueShellInputPutAndRead() throws Exception {
        BlockingQueueShellInput input = new BlockingQueueShellInput(10);
        input.putText("hello\n");
        input.sendEof();

        assertNull(input.read(), "After sendEof, read should return null immediately");
    }

    @Test
    void testBlockingQueueShellInputReadLineBeforeEof() throws Exception {
        BlockingQueueShellInput input = new BlockingQueueShellInput(10);
        input.putText("hello\n");

        assertEquals("hello", input.readLine());

        Thread eofThread = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            input.sendEof();
        });
        eofThread.start();

        assertNull(input.readLine(), "Should return null after EOF");
        eofThread.join(1000);
    }

    @Test
    void testBlockingQueueShellInputSendEofThenReadReturnsNull() throws Exception {
        BlockingQueueShellInput input = new BlockingQueueShellInput(10);
        input.sendEof();
        assertNull(input.read());
    }

    @Test
    void testBlockingQueueShellInputPutAfterEofThrows() throws Exception {
        BlockingQueueShellInput input = new BlockingQueueShellInput(10);
        input.sendEof();
        assertThrows(IllegalStateException.class, () -> input.putText("x"));
    }

    @Test
    void testFileShellOutputOverwriteMode() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("test", ".txt");
        tmp.toFile().deleteOnExit();
        java.nio.file.Files.writeString(tmp, "old line\n");

        FileShellOutput output = new FileShellOutput(tmp);
        output.println("new line 1");
        output.println("new line 2");
        output.close();

        String content = java.nio.file.Files.readString(tmp);
        assertFalse(content.contains("old"), "Old content should be truncated");
        assertTrue(content.contains("new line 1"));
        assertTrue(content.contains("new line 2"));
    }

    @Test
    void testFileShellOutputAppendMode() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("test", ".txt");
        tmp.toFile().deleteOnExit();
        java.nio.file.Files.writeString(tmp, "existing\n");

        FileShellOutput output = new FileShellOutput(tmp, true);
        output.println("appended");
        output.close();

        String content = java.nio.file.Files.readString(tmp);
        assertTrue(content.contains("existing"));
        assertTrue(content.contains("appended"));
    }

    @Test
    void testTeeOutputWritesToAll() {
        ListShellOutput out1 = new ListShellOutput();
        ListShellOutput out2 = new ListShellOutput();
        TeeOutput tee = new TeeOutput(out1, out2);

        tee.println("broadcast");
        tee.close();

        assertTrue(out1.getTextContent().contains("broadcast"));
        assertTrue(out2.getTextContent().contains("broadcast"));
    }

    @Test
    void testDuplexShellOutputDelegates() {
        ListShellOutput target = new ListShellOutput();
        DuplexShellOutput duplex = new DuplexShellOutput(target, false);
        duplex.println("delegated");
        assertEquals("delegated\n", target.getTextContent());
    }

    @Test
    void testDuplexShellOutputOwnedClose() {
        ListShellOutput target = new ListShellOutput();
        DuplexShellOutput duplex = new DuplexShellOutput(target, true);
        duplex.println("x");
        duplex.close();
        assertThrows(IllegalStateException.class, () -> target.write(ShellChunk.text("y")));
    }

    @Test
    void testDuplexShellOutputNotOwnedNoClose() {
        ListShellOutput target = new ListShellOutput();
        DuplexShellOutput duplex = new DuplexShellOutput(target, false);
        duplex.println("x");
        duplex.close();
        target.write(ShellChunk.text("y"));
    }

    @Test
    void testLinesIterator() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.println("a");
        output.println("b");
        output.close();

        AbstractShellInput input = (AbstractShellInput) output.asInput();
        var lines = input.lines();
        assertTrue(lines.hasNext());
        assertEquals("a", lines.next());
        assertTrue(lines.hasNext());
        assertEquals("b", lines.next());
        assertFalse(lines.hasNext());
    }

    @Test
    void testChunksIterator() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        output.print("chunk1");
        output.print("chunk2");
        output.close();

        AbstractShellInput input = (AbstractShellInput) output.asInput();
        var chunks = input.chunks();
        assertTrue(chunks.hasNext());
        assertEquals("chunk1", chunks.next().asText());
        assertTrue(chunks.hasNext());
        assertEquals("chunk2", chunks.next().asText());
        assertFalse(chunks.hasNext());
    }

    @Test
    void testPrintStreamShellOutput() {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream ps = new java.io.PrintStream(baos);
        PrintStreamShellOutput output = new PrintStreamShellOutput(ps);
        output.println("test line");
        output.flush();
        assertTrue(baos.toString().contains("test line"));
        assertThrows(UnsupportedOperationException.class, output::asInput);
    }
}
