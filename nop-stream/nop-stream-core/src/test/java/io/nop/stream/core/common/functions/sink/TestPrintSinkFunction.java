package io.nop.stream.core.common.functions.sink;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class TestPrintSinkFunction {

    @Test
    void testPrintsWithoutPrefix() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(baos));
        try {
            PrintSinkFunction<String> sink = new PrintSinkFunction<>();
            sink.consume("hello");
            assertEquals("hello" + System.lineSeparator(), baos.toString());
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testPrintsWithPrefix() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(baos));
        try {
            PrintSinkFunction<Integer> sink = new PrintSinkFunction<>("SINK-1");
            sink.consume(42);
            assertEquals("SINK-1: 42" + System.lineSeparator(), baos.toString());
        } finally {
            System.setOut(originalOut);
        }
    }
}
