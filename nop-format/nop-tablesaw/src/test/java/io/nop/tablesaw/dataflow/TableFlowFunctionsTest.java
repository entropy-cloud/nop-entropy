package io.nop.tablesaw.dataflow;

import org.junit.jupiter.api.Test;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TableFlowFunctionsTest {

    static Table makeTable() {
        return Table.create("test",
                StringColumn.create("city", new String[]{"New York", "London", "Tokyo", "New York", "London", "Paris"}),
                StringColumn.create("country", new String[]{"USA", "UK", "Japan", "USA", "UK", "France"})
        );
    }

    static Table makeDirtyTable() {
        return Table.create("dirty",
                StringColumn.create("name", new String[]{"Alice", "ALICE", "alice", "Bob", "BOB", "Charlie", null, "   David  "}),
                StringColumn.create("email", new String[]{"a@x.com", "a@x.com", "b@y.com", "c@z.com", null, null, null, "d@w.com"})
        );
    }

    @Test
    public void testFacetValue() {
        Table t = makeTable();
        List<Map<String, Object>> result = TableFlowFunctions.facetValue(t, "city");
        assertEquals(4, result.size());
        assertEquals("New York", result.get(0).get("value"));
        assertEquals(2, result.get(0).get("count"));
    }

    @Test
    public void testPreview() {
        Table t = makeTable();
        List<Map<String, Object>> result = TableFlowFunctions.preview(t, 2);
        assertEquals(2, result.size());
        assertEquals("New York", result.get(0).get("city"));
        assertEquals("USA", result.get(0).get("country"));
    }

    @Test
    public void testSummary() {
        Table t = makeTable();
        List<Map<String, Object>> result = TableFlowFunctions.summary(t);
        assertEquals(2, result.size());
        assertEquals("city", result.get(0).get("column"));
        assertEquals("STRING", result.get(0).get("type"));
    }

    @Test
    public void testMissing() {
        Table t = makeDirtyTable();
        List<Map<String, Object>> result = TableFlowFunctions.missing(t);
        assertEquals(2, result.size());
        Map<String, Object> email = result.get(1);
        assertEquals("email", email.get("column"));
        assertEquals(3, email.get("missing"));
    }

    @Test
    public void testTransformTrim() {
        Table t = makeDirtyTable();
        Table result = TableFlowFunctions.transform(t, "name", "trim");
        StringColumn col = result.stringColumn("name");
        assertEquals("David", col.get(7));
    }

    @Test
    public void testTransformUpper() {
        Table t = makeDirtyTable();
        Table result = TableFlowFunctions.transform(t, "name", "upper");
        StringColumn col = result.stringColumn("name");
        assertEquals("ALICE", col.get(0));
        assertEquals("ALICE", col.get(1));
    }

    @Test
    public void testReplace() {
        Table t = makeDirtyTable();
        Table result = TableFlowFunctions.replace(t, "email", "@.*", "@redacted.com");
        StringColumn col = result.stringColumn("email");
        assertTrue(col.get(0).contains("redacted"));
    }

    @Test
    public void testSplit() {
        Table t = makeTable();
        // "New York" splits into ["New", "York"]
        Table result = TableFlowFunctions.split(t, "city", " ");
        assertTrue(result.columnNames().contains("city_1"));
        assertTrue(result.columnNames().contains("city_2"));
        assertEquals("New", result.stringColumn("city_1").get(0));
        assertEquals("York", result.stringColumn("city_2").get(0));
    }

    @Test
    public void testClusterKeyCollision() {
        Table t = makeDirtyTable();
        List<TableFlowFunctions.ClusterGroup> groups = TableFlowFunctions.clusterKeyCollision(t, "name");
        assertTrue(groups.size() >= 1);
        boolean foundAlice = false;
        for (TableFlowFunctions.ClusterGroup g : groups) {
            if (g.getMembers().stream().anyMatch(m -> "Alice".equals(m.get("value")))) {
                foundAlice = true;
                break;
            }
        }
        assertTrue(foundAlice);
    }

    @Test
    public void testClusterNearestNeighbor() {
        Table t = makeDirtyTable();
        List<TableFlowFunctions.ClusterGroup> groups = TableFlowFunctions.clusterNearestNeighbor(t, "name", 2);
        assertTrue(groups.size() >= 1);
    }

    @Test
    public void testFacetNumeric() {
        Table t = Table.create("nums",
                tech.tablesaw.api.DoubleColumn.create("score", new double[]{1.0, 2.5, 3.0, 4.2, 5.0, 6.7})
        );
        List<Map<String, Object>> result = TableFlowFunctions.facetNumeric(t, "score", 2.0);
        assertTrue(result.size() >= 2);
    }
}
