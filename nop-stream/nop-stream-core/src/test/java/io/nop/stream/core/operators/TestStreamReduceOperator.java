package io.nop.stream.core.operators;

import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestStreamReduceOperator {

    private StreamReduceOperator<Integer> operator;
    private TestOutput<Integer> output;

    @BeforeEach
    void setUp() throws Exception {
        ReduceFunction<Integer> sum = (value1, value2) -> value1 + value2;
        operator = new StreamReduceOperator<>(sum);
        output = new TestOutput<>();
        operator.setOutput((Output) output);
        operator.open();
    }

    private void processElement(int key, int value) throws Exception {
        operator.setCurrentKey(key);
        operator.processElement(new StreamRecord<>(value, System.currentTimeMillis()));
    }

    @Test
    void testReduceAccumulates() throws Exception {
        processElement(1, 10);
        processElement(1, 20);
        processElement(1, 30);

        assertEquals(3, output.getRecords().size());
        assertEquals(10, output.getRecords().get(0).getValue());
        assertEquals(30, output.getRecords().get(1).getValue());
        assertEquals(60, output.getRecords().get(2).getValue());
    }

    @Test
    void testReduceMultipleKeys() throws Exception {
        processElement(1, 10);
        processElement(2, 100);
        processElement(1, 20);
        processElement(2, 200);

        assertEquals(4, output.getRecords().size());
    }

    @Test
    void testSnapshotRestorePreservesState() throws Exception {
        processElement(1, 10);
        processElement(2, 100);
        processElement(1, 20);

        OperatorSnapshotResult snapshot = operator.snapshotState(
                new StateSnapshotContext(1L, System.currentTimeMillis()));

        ReduceFunction<Integer> sum = (value1, value2) -> value1 + value2;
        StreamReduceOperator<Integer> restored = new StreamReduceOperator<>(sum);
        TestOutput<Integer> restoredOutput = new TestOutput<>();
        restored.setOutput((Output) restoredOutput);
        restored.open();
        restored.restoreState(snapshot);

        restored.setCurrentKey(1);
        restored.processElement(new StreamRecord<>(5, System.currentTimeMillis()));
        restored.setCurrentKey(2);
        restored.processElement(new StreamRecord<>(50, System.currentTimeMillis()));

        assertEquals(2, restoredOutput.getRecords().size());
        assertEquals(35, restoredOutput.getRecords().get(0).getValue());
        assertEquals(150, restoredOutput.getRecords().get(1).getValue());
    }

    @Test
    void testSnapshotRestoreEmptyOperator() throws Exception {
        OperatorSnapshotResult snapshot = operator.snapshotState(
                new StateSnapshotContext(1L, System.currentTimeMillis()));

        ReduceFunction<Integer> sum = (value1, value2) -> value1 + value2;
        StreamReduceOperator<Integer> restored = new StreamReduceOperator<>(sum);
        TestOutput<Integer> restoredOutput = new TestOutput<>();
        restored.setOutput((Output) restoredOutput);
        restored.open();
        restored.restoreState(snapshot);

        restored.setCurrentKey(1);
        restored.processElement(new StreamRecord<>(42, System.currentTimeMillis()));

        assertEquals(1, restoredOutput.getRecords().size());
        assertEquals(42, restoredOutput.getRecords().get(0).getValue());
    }

    @Test
    void testRestoreFromNullSnapshot() throws Exception {
        OperatorSnapshotResult empty = new OperatorSnapshotResult();

        ReduceFunction<Integer> sum = (value1, value2) -> value1 + value2;
        StreamReduceOperator<Integer> restored = new StreamReduceOperator<>(sum);
        TestOutput<Integer> restoredOutput = new TestOutput<>();
        restored.setOutput((Output) restoredOutput);
        restored.open();
        restored.restoreState(empty);

        restored.setCurrentKey(1);
        restored.processElement(new StreamRecord<>(7, System.currentTimeMillis()));

        assertEquals(1, restoredOutput.getRecords().size());
        assertEquals(7, restoredOutput.getRecords().get(0).getValue());
    }

    @Test
    void testOperatorSnapshotResultJsonMethods() throws Exception {
        OperatorSnapshotResult result = new OperatorSnapshotResult();

        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        result.putOperatorState("test-state", JsonTool.stringify(map));

        String json = (String) result.getOperatorState("test-state");
        java.util.Map<?, ?> restored = JsonTool.parseBeanFromText(json, java.util.Map.class);
        assertNotNull(restored);
        assertEquals(1, restored.get("a"));
        assertEquals(2, restored.get("b"));
    }
}
