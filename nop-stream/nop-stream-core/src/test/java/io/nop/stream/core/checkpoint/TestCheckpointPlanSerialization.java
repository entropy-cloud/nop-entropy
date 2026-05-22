package io.nop.stream.core.checkpoint;

import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointPlanSerialization {

    @Test
    void testTaskLocationJsonRoundTripViaJsonTool() {
        TaskLocation original = new TaskLocation("job-1", "pipeline-1", "vertex-A", 0);
        String json = JsonTool.serialize(original, false);
        assertNotNull(json);
        assertTrue(json.contains("job-1"));
        assertTrue(json.contains("vertex-A"));
    }

    @Test
    void testOperatorStateMappingJsonRoundTripViaJsonTool() {
        OperatorStateMapping original = new OperatorStateMapping(2, "operator-2", "operator-2-keyed", true);
        String json = JsonTool.serialize(original, false);
        assertNotNull(json);
        assertTrue(json.contains("operator-2"));
        assertTrue(json.contains("operator-2-keyed"));
    }

    @Test
    void testCheckpointPlanJsonRoundTripViaJackson() throws Exception {
        TaskLocation loc1 = new TaskLocation("job-1", "pipeline-1", "vertex-source", 0);
        TaskLocation loc2 = new TaskLocation("job-1", "pipeline-1", "vertex-map", 0);

        List<TaskLocation> allTasks = Arrays.asList(loc1, loc2);
        List<TaskLocation> sourceTasks = Collections.singletonList(loc1);

        Map<TaskLocation, List<OperatorStateMapping>> stateMappings = new LinkedHashMap<>();
        stateMappings.put(loc1, Arrays.asList(
                new OperatorStateMapping(0, "operator-0", null, false),
                new OperatorStateMapping(1, "operator-1", "operator-1-keyed", false)
        ));
        stateMappings.put(loc2, Collections.singletonList(
                new OperatorStateMapping(0, "operator-0", null, false)
        ));

        CheckpointPlan original = new CheckpointPlan(1, "job-1", "pipeline-1", allTasks, sourceTasks, stateMappings);

        String json = JsonTool.serialize(original, false);
        assertNotNull(json);
        assertTrue(json.contains("job-1"));
        assertTrue(json.contains("pipeline-1"));
    }

    @Test
    void testCheckpointPlanEquality() {
        TaskLocation loc1 = new TaskLocation("job-1", "p-1", "v-source", 0);
        TaskLocation loc2 = new TaskLocation("job-1", "p-1", "v-map", 0);

        List<TaskLocation> allTasks = Arrays.asList(loc1, loc2);
        List<TaskLocation> sourceTasks = Collections.singletonList(loc1);

        Map<TaskLocation, List<OperatorStateMapping>> stateMappings = new LinkedHashMap<>();
        stateMappings.put(loc1, Arrays.asList(
                new OperatorStateMapping(0, "operator-0", null, false)
        ));
        stateMappings.put(loc2, Arrays.asList(
                new OperatorStateMapping(0, "operator-0", null, false)
        ));

        CheckpointPlan plan1 = new CheckpointPlan("job-1", "p-1", allTasks, sourceTasks, stateMappings);
        CheckpointPlan plan2 = new CheckpointPlan("job-1", "p-1", allTasks, sourceTasks, stateMappings);

        assertEquals(plan1.getJobId(), plan2.getJobId());
        assertEquals(plan1.getPipelineId(), plan2.getPipelineId());
        assertEquals(plan1.getAllTasks(), plan2.getAllTasks());
        assertEquals(plan1.getSourceTasks(), plan2.getSourceTasks());
        assertEquals(plan1.getStateMappings().size(), plan2.getStateMappings().size());
    }

    @Test
    void testTaskLocationAsMapKey() {
        TaskLocation loc1 = new TaskLocation("job-1", "p-1", "v-1", 0);
        TaskLocation loc2 = new TaskLocation("job-1", "p-1", "v-2", 0);

        Map<TaskLocation, String> map = new LinkedHashMap<>();
        map.put(loc1, "value-1");
        map.put(loc2, "value-2");

        assertEquals(2, map.size());
        assertEquals("value-1", map.get(loc1));
        assertEquals("value-2", map.get(loc2));
        assertEquals("value-1", map.get(new TaskLocation("job-1", "p-1", "v-1", 0)));
    }
}
