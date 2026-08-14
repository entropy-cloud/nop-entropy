// Fixture: intentionally violating sample for check-nop-stream-invariants.mjs self-test.
// This file is NOT part of the nop-stream source tree (lives under ai-dev fixtures);
// it exists so the scanner's self-test can prove "能红" against a deliberate
// un-synchronized iteration of a Collections.synchronized* collection (invariant #2).
// NOT scanned by the normal `all` / `scan-iterations` run (scan scope = module src/main).

package fixtures;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class BadSinkFixture {

    private final Map<Long, Object> pending = Collections.synchronizedMap(new TreeMap<>());

    public void saveState() {
        // VIOLATION: copy iteration of a synchronized collection outside any synchronized block
        Map<Long, Object> copy = new TreeMap<>(pending);
        if (copy.isEmpty()) {
            return;
        }
    }

    public void goodPath(long epochId) {
        Map<Long, Object> p = getPending();
        synchronized (p) {
            for (Map.Entry<Long, Object> entry : p.entrySet()) {
                if (entry.getKey() <= epochId) {
                    p.remove(entry.getKey());
                }
            }
        }
    }

    public Map<Long, Object> getPending() {
        return pending;
    }
}
