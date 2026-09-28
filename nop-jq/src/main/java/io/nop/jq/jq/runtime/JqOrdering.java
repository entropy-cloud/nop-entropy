package io.nop.jq.jq.runtime;

import java.util.Map;

/**
 * jq total ordering: null &lt; false &lt; true &lt; numbers &lt; strings
 * &lt; arrays &lt; objects. NaN orders after all other numbers and equals
 * itself (sort stability). Objects compare key arrays first, then values.
 */
public final class JqOrdering {
    private JqOrdering() {
    }

    public static int compare(JqValue a, JqValue b) {
        int ra = rank(a);
        int rb = rank(b);
        if (ra != rb)
            return Integer.compare(ra, rb);
        switch (ra) {
            case 0:
                return 0; // null
            case 1:
            case 2:
                return 0; // booleans of equal truth
            case 3:
                return compareNumbers((JqNumber) a, (JqNumber) b);
            case 4:
                return ((JqString) a).value().compareTo(((JqString) b).value());
            case 5: {
                JqArray aa = (JqArray) a;
                JqArray ab = (JqArray) b;
                int n = Math.min(aa.size(), ab.size());
                for (int i = 0; i < n; i++) {
                    int cmp = compare(aa.get(i), ab.get(i));
                    if (cmp != 0)
                        return cmp;
                }
                return Integer.compare(aa.size(), ab.size());
            }
            default: {
                JqObject oa = (JqObject) a;
                JqObject ob = (JqObject) b;
                int cmp = JqPrinter.compareLists(sortedKeys(oa), sortedKeys(ob));
                if (cmp != 0)
                    return cmp;
                for (String key : oa.keySet()) {
                    cmp = compare(oa.get(key), ob.get(key));
                    if (cmp != 0)
                        return cmp;
                }
                return 0;
            }
        }
    }

    private static int compareNumbers(JqNumber a, JqNumber b) {
        double da = a.doubleValue();
        double db = b.doubleValue();
        // exact comparison only between two literal integers
        if (a.isLiteralInteger() && b.isLiteralInteger()) {
            return Long.compare(a.longValue(), b.longValue());
        }
        boolean na = Double.isNaN(da);
        boolean nb = Double.isNaN(db);
        if (na && nb)
            return 0;
        if (na)
            return 1; // NaN sorts after every number
        if (nb)
            return -1;
        return Double.compare(da, db);
    }

    private static java.util.List<JqValue> sortedKeys(JqObject obj) {
        java.util.List<String> keys = new java.util.ArrayList<>(obj.keySet());
        java.util.Collections.sort(keys);
        java.util.List<JqValue> result = new java.util.ArrayList<>(keys.size());
        for (String key : keys)
            result.add(JqString.of(key));
        return result;
    }

    private static int rank(JqValue v) {
        if (v instanceof JqNull)
            return 0;
        if (v instanceof JqBoolean b)
            return b.value() ? 2 : 1;
        if (v instanceof JqNumber)
            return 3;
        if (v instanceof JqString)
            return 4;
        if (v instanceof JqArray)
            return 5;
        if (v instanceof JqObject)
            return 6;
        return 7; // functions: unspecified
    }
}
