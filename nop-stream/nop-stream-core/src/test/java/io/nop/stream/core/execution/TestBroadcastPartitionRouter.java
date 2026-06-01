package io.nop.stream.core.execution;

import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestBroadcastPartitionRouter {

    @Test
    void testCreateReturnsBroadcastPartitionRouter() {
        PartitionRouter router = PartitionRouter.create(PartitionPolicy.BROADCAST, 4, null, 0);
        assertNotNull(router, "BROADCAST policy should create a PartitionRouter");
        assertEquals(4, router.getNumberOfPartitions());
        assertEquals(0, router.selectChannel(new StreamRecord<>("test", 0)));
    }

    @Test
    void testSelectChannelReturnsZero() {
        PartitionRouter router = PartitionRouter.create(PartitionPolicy.BROADCAST, 5, null, 0);
        assertEquals(0, router.selectChannel(new StreamRecord<>("test", 0)));
    }

    @Test
    void testRecordWriterBroadcastsToAllPartitions() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();

        PartitionRouter router = PartitionRouter.create(PartitionPolicy.BROADCAST, 3, null, 0);
        RecordWriter<String> writer = new RecordWriter<>(
                new ResultPartition[]{p0, p1, p2}, null, null, router);

        writer.emit(new StreamRecord<>("broadcast-data", 0));

        p0.close();
        p1.close();
        p2.close();

        List<StreamElement> collected0 = new ArrayList<>();
        List<StreamElement> collected1 = new ArrayList<>();
        List<StreamElement> collected2 = new ArrayList<>();

        StreamElement e;
        while ((e = p0.read()) != null) collected0.add(e);
        while ((e = p1.read()) != null) collected1.add(e);
        while ((e = p2.read()) != null) collected2.add(e);

        assertEquals(1, collected0.size(), "Partition 0 should receive broadcast");
        assertEquals(1, collected1.size(), "Partition 1 should receive broadcast");
        assertEquals(1, collected2.size(), "Partition 2 should receive broadcast");

        for (List<StreamElement> list : Arrays.asList(collected0, collected1, collected2)) {
            StreamElement elem = list.get(0);
            assertTrue(elem.isRecord());
            assertEquals("broadcast-data", elem.asRecord().getValue());
        }
    }

    @Test
    void testForwardNotAffectedByBroadcast() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();

        PartitionRouter router = PartitionRouter.create(PartitionPolicy.FORWARD, 2, null, 0);
        RecordWriter<String> writer = new RecordWriter<>(
                new ResultPartition[]{p0, p1}, null, null, router);

        writer.emit(new StreamRecord<>("forward-data", 0));

        p0.close();
        p1.close();

        int count0 = 0, count1 = 0;
        StreamElement e;
        while ((e = p0.read()) != null) count0++;
        while ((e = p1.read()) != null) count1++;

        assertEquals(1, count0 + count1, "FORWARD should send to exactly one partition");
    }

    @Test
    void testNullPolicyFallsBackToForward() {
        PartitionRouter router = PartitionRouter.create(null, 2, null, 0);
        assertNotNull(router, "Null policy should not return null");
        assertEquals(2, router.getNumberOfPartitions());
    }
}
