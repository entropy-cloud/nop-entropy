package io.nop.tablesaw.dataflow;

import tech.tablesaw.api.NumericColumn;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class TableFlowFunctions {

    // ========== Exploration / Observation ==========

    public static List<Map<String, Object>> facetValue(Table table, String column) {
        Table result = table.countBy(column).sortDescendingOn("Count");
        List<Map<String, Object>> list = new ArrayList<>();
        for (Row row : result) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", row.getObject(column));
            entry.put("count", row.getInt("Count"));
            list.add(entry);
        }
        return list;
    }

    public static List<Map<String, Object>> facetNumeric(Table table, String column, double step) {
        NumericColumn<?> col = table.numberColumn(column);
        double min = col.min();
        double max = col.max();
        List<Map<String, Object>> list = new ArrayList<>();
        if (Double.isNaN(min) || Double.isNaN(max) || step <= 0)
            return list;

        for (double start = min; start < max; start += step) {
            double end = Math.min(start + step, max);
            double rangeStart = start;
            double rangeEnd = end;
            long count = 0;
            for (int i = 0; i < col.size(); i++) {
                if (!col.isMissing(i)) {
                    double v = col.getDouble(i);
                    if (v >= rangeStart && v < rangeEnd) count++;
                }
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("range", String.format("[%.2f, %.2f)", rangeStart, rangeEnd));
            entry.put("count", count);
            list.add(entry);
        }
        return list;
    }

    public static List<Map<String, Object>> summary(Table table) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Column<?> col : table.columns()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("column", col.name());
            entry.put("type", col.type().name());
            entry.put("size", col.size());
            entry.put("missing", col.countMissing());
            entry.put("unique", uniqueCount(col));
            if (col instanceof NumericColumn) {
                NumericColumn<?> n = (NumericColumn<?>) col;
                entry.put("min", n.min());
                entry.put("max", n.max());
                entry.put("mean", n.mean());
                entry.put("stdDev", n.standardDeviation());
            }
            list.add(entry);
        }
        return list;
    }

    public static List<Map<String, Object>> preview(Table table, int n) {
        int limit = Math.min(n, table.rowCount());
        List<Map<String, Object>> list = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Row row = table.row(i);
            Map<String, Object> map = new LinkedHashMap<>();
            for (Column<?> col : table.columns()) {
                map.put(col.name(), row.getObject(col.name()));
            }
            list.add(map);
        }
        return list;
    }

    // ========== Data Quality ==========

    public static List<Map<String, Object>> missing(Table table) {
        List<Map<String, Object>> list = new ArrayList<>();
        int total = table.rowCount();
        for (Column<?> col : table.columns()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("column", col.name());
            entry.put("type", col.type().name());
            int missing = col.countMissing();
            entry.put("missing", missing);
            entry.put("missingPercent", total > 0 ? (double) Math.round(1000.0 * missing / total) / 10 : 0.0);
            list.add(entry);
        }
        return list;
    }

    // ========== Clustering ==========

    public static List<ClusterGroup> clusterKeyCollision(Table table, String column) {
        Column<?> col = table.column(column);
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < col.size(); i++) {
            Object val = col.get(i);
            if (val == null) continue;
            String fingerprint = fingerprint(val.toString());
            groups.computeIfAbsent(fingerprint, k -> new ArrayList<>()).add(i);
        }
        return toClusterGroups(table, column, groups);
    }

    public static List<ClusterGroup> clusterNearestNeighbor(Table table, String column, int maxDistance) {
        StringColumn col = table.stringColumn(column);
        List<ClusterGroup> groups = new ArrayList<>();
        boolean[] assigned = new boolean[col.size()];

        for (int i = 0; i < col.size(); i++) {
            if (assigned[i]) continue;
            String base = col.get(i);
            if (base == null) continue;

            List<Integer> members = new ArrayList<>();
            members.add(i);
            assigned[i] = true;

            for (int j = i + 1; j < col.size(); j++) {
                if (assigned[j]) continue;
                String other = col.get(j);
                if (other != null && levenshtein(base, other) <= maxDistance) {
                    members.add(j);
                    assigned[j] = true;
                }
            }

            if (members.size() > 1) {
                groups.add(buildGroup(table, column, members));
            }
        }
        groups.sort(Comparator.comparingInt(g -> -g.members.size()));
        return groups;
    }

    // ========== Transformation ==========

    public static Table transform(Table table, String column, String operation) {
        Column<?> col = table.column(column);
        if (!(col instanceof StringColumn))
            throw new IllegalArgumentException("transform only supports StringColumn, got " + col.type());

        StringColumn strCol = (StringColumn) col;
        StringColumn updated;
        switch (operation) {
            case "trim":
                updated = strCol.trim();
                break;
            case "upper":
                updated = strCol.upperCase();
                break;
            case "lower":
                updated = strCol.lowerCase();
                break;
            case "removeWhitespace":
                updated = strCol.replaceAll("\\s+", "");
                break;
            case "collapseWhitespace":
                updated = strCol.replaceAll("\\s+", " ").trim();
                break;
            case "toTitleCase":
                updated = toTitleCaseColumn(strCol);
                break;
            default:
                throw new IllegalArgumentException("Unknown transform operation: " + operation);
        }
        Table copy = table.copy();
        copy.replaceColumn(column, updated.setName(column));
        return copy;
    }

    public static Table replace(Table table, String column, String regex, String replacement) {
        Column<?> col = table.column(column);
        if (!(col instanceof StringColumn))
            throw new IllegalArgumentException("replace only supports StringColumn");

        StringColumn updated = ((StringColumn) col).replaceAll(regex, replacement);
        Table copy = table.copy();
        copy.replaceColumn(column, updated.setName(column));
        return copy;
    }

    public static Table split(Table table, String column, String separator) {
        Column<?> col = table.column(column);
        if (!(col instanceof StringColumn))
            throw new IllegalArgumentException("split only supports StringColumn");

        StringColumn strCol = (StringColumn) col;
        List<String[]> parts = new ArrayList<>(strCol.size());
        int maxParts = 0;
        for (String v : strCol) {
            if (v == null) {
                parts.add(new String[0]);
            } else {
                String[] p = v.split(Pattern.quote(separator), -1);
                parts.add(p);
                maxParts = Math.max(maxParts, p.length);
            }
        }

        Table copy = table.copy();
        int colIndex = copy.columnIndex(column);

        List<StringColumn> newCols = new ArrayList<>(maxParts);
        for (int i = 0; i < maxParts; i++) {
            String name = column + "_" + (i + 1);
            String[] values = new String[strCol.size()];
            for (int r = 0; r < strCol.size(); r++) {
                String[] p = parts.get(r);
                values[r] = i < p.length ? p[i] : null;
            }
            newCols.add(StringColumn.create(name, values));
        }

        copy.addColumns(newCols.toArray(new StringColumn[0]));
        return copy;
    }

    // ========== Utility ==========

    private static StringColumn toTitleCaseColumn(StringColumn col) {
        String[] values = new String[col.size()];
        for (int i = 0; i < col.size(); i++) {
            String v = col.get(i);
            if (v == null || v.isEmpty()) {
                values[i] = v;
            } else {
                String[] words = v.toLowerCase().split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (String word : words) {
                    if (!word.isEmpty()) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(Character.toUpperCase(word.charAt(0)));
                        if (word.length() > 1) sb.append(word.substring(1));
                    }
                }
                values[i] = sb.toString();
            }
        }
        return StringColumn.create(col.name(), values);
    }

    private static int uniqueCount(Column<?> col) {
        Set<Object> set = new HashSet<>();
        for (int i = 0; i < col.size(); i++) {
            set.add(col.get(i));
        }
        return set.size();
    }

    private static String fingerprint(String s) {
        if (s == null) return "";
        String normalized = s.toLowerCase().trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        char[] chars = sb.toString().toCharArray();
        java.util.Arrays.sort(chars);
        return new String(chars);
    }

    private static int levenshtein(String a, String b) {
        if (a == null) return b == null ? 0 : b.length();
        if (b == null) return a.length();
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private static List<ClusterGroup> toClusterGroups(Table table, String column, Map<String, List<Integer>> groups) {
        List<ClusterGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : groups.entrySet()) {
            if (entry.getValue().size() <= 1) continue;
            result.add(buildGroup(table, column, entry.getValue()));
        }
        result.sort(Comparator.comparingInt(g -> -g.members.size()));
        return result;
    }

    private static ClusterGroup buildGroup(Table table, String column, List<Integer> rowIndices) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (int idx : rowIndices) {
            Row row = table.row(idx);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("row", idx);
            map.put("value", row.getObject(column));
            members.add(map);
        }
        return new ClusterGroup(members);
    }

    public static class ClusterGroup {
        private List<Map<String, Object>> members;

        public ClusterGroup() {}

        public ClusterGroup(List<Map<String, Object>> members) {
            this.members = members;
        }

        public List<Map<String, Object>> getMembers() {
            return members;
        }

        public void setMembers(List<Map<String, Object>> members) {
            this.members = members;
        }

        public int size() {
            return members != null ? members.size() : 0;
        }
    }
}
