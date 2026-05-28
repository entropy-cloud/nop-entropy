package io.nop.stream.cep.operator;

import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStreamRecordComparator {

    private final StreamRecordComparator<String> comparator = new StreamRecordComparator<>();

    @Test
    public void testAscendingTimestampOrder() {
        StreamRecord<String> r1 = new StreamRecord<>("a", 100L);
        StreamRecord<String> r2 = new StreamRecord<>("b", 200L);
        StreamRecord<String> r3 = new StreamRecord<>("c", 300L);

        assertTrue(comparator.compare(r1, r2) < 0);
        assertTrue(comparator.compare(r2, r3) < 0);
        assertTrue(comparator.compare(r1, r3) < 0);
        assertTrue(comparator.compare(r3, r1) > 0);
        assertTrue(comparator.compare(r2, r1) > 0);
    }

    @Test
    public void testEqualTimestamps() {
        StreamRecord<String> r1 = new StreamRecord<>("a", 100L);
        StreamRecord<String> r2 = new StreamRecord<>("b", 100L);

        assertEquals(0, comparator.compare(r1, r2));
        assertEquals(0, comparator.compare(r2, r1));
    }

    @Test
    public void testSameRecord() {
        StreamRecord<String> r1 = new StreamRecord<>("a", 100L);
        assertEquals(0, comparator.compare(r1, r1));
    }

    @Test
    public void testSortListByTimestamp() {
        List<StreamRecord<String>> records = new ArrayList<>();
        records.add(new StreamRecord<>("c", 300L));
        records.add(new StreamRecord<>("a", 100L));
        records.add(new StreamRecord<>("b", 200L));

        Collections.sort(records, comparator);

        assertEquals(100L, records.get(0).getTimestamp());
        assertEquals(200L, records.get(1).getTimestamp());
        assertEquals(300L, records.get(2).getTimestamp());
        assertEquals("a", records.get(0).getValue());
        assertEquals("b", records.get(1).getValue());
        assertEquals("c", records.get(2).getValue());
    }

    @Test
    public void testSortWithDuplicateTimestamps() {
        List<StreamRecord<String>> records = new ArrayList<>();
        records.add(new StreamRecord<>("c", 100L));
        records.add(new StreamRecord<>("a", 100L));
        records.add(new StreamRecord<>("b", 100L));

        Collections.sort(records, comparator);

        assertEquals(100L, records.get(0).getTimestamp());
        assertEquals(100L, records.get(1).getTimestamp());
        assertEquals(100L, records.get(2).getTimestamp());
    }

    @Test
    public void testZeroTimestamps() {
        StreamRecord<String> r1 = new StreamRecord<>("a", 0L);
        StreamRecord<String> r2 = new StreamRecord<>("b", 0L);

        assertEquals(0, comparator.compare(r1, r2));
    }

    @Test
    public void testNegativeTimestamps() {
        StreamRecord<String> r1 = new StreamRecord<>("a", -100L);
        StreamRecord<String> r2 = new StreamRecord<>("b", 100L);

        assertTrue(comparator.compare(r1, r2) < 0);
        assertTrue(comparator.compare(r2, r1) > 0);
    }
}
