package io.nop.dataset.record.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBaseRecordInput {

    private BaseRecordInput<String> newInput(List<String> records) {
        return new BaseRecordInput<>(records, null);
    }

    @Test
    public void testEmptyList() {
        BaseRecordInput<String> input = newInput(Collections.emptyList());

        assertFalse(input.hasNext());
        assertThrows(NoSuchElementException.class, input::next);
        assertEquals(0, input.getReadCount());
        assertEquals(0, input.getTotalCount());
        assertEquals(Collections.emptyList(), input.readAll());
    }

    @Test
    public void testSingleElement() {
        BaseRecordInput<String> input = newInput(List.of("a"));

        assertTrue(input.hasNext());
        assertEquals("a", input.next());
        assertFalse(input.hasNext());
        assertEquals(1, input.getReadCount());
        assertEquals(1, input.getTotalCount());
        assertThrows(NoSuchElementException.class, input::next);
    }

    @Test
    public void testMultiElementIteration() {
        BaseRecordInput<String> input = newInput(List.of("a", "b", "c"));

        List<String> consumed = new ArrayList<>();
        while (input.hasNext()) {
            consumed.add(input.next());
        }
        assertEquals(List.of("a", "b", "c"), consumed);
        assertEquals(3, input.getReadCount());
        assertEquals(3, input.getTotalCount());
        assertThrows(NoSuchElementException.class, input::next);
    }

    @Test
    public void testReadAllDefaultPath() {
        assertEquals(List.of("a", "b", "c"), newInput(List.of("a", "b", "c")).readAll());
        assertEquals(Collections.emptyList(), newInput(Collections.emptyList()).readAll());
        assertEquals(List.of("a"), newInput(List.of("a")).readAll());
    }

    @Test
    public void testReadBatchDefaultPath() {
        BaseRecordInput<String> input = newInput(List.of("a", "b", "c"));

        assertEquals(List.of("a", "b"), input.readBatch(2));
        assertEquals(2, input.getReadCount());
        assertEquals(List.of("c"), input.readBatch(2));
        assertEquals(3, input.getReadCount());
        assertEquals(Collections.emptyList(), input.readBatch(2));
    }
}
