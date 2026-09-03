// Fixture: intentionally violating sample for check-nop-stream-invariants.mjs self-test.
// This file is NOT part of the nop-stream source tree (lives under ai-dev fixtures);
// it exists so the wildcard-import gate's self-test can prove "能红" against deliberately
// injected on-demand imports (roadmap item 22: test wildcard imports must stay 0).
// NOT scanned by the normal `all` / `check-wildcard-imports` module run (scope = nop-stream modules).

package fixtures;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class WildcardImportFixture {

    // VIOLATION (type wildcard): import java.util.* above
    // VIOLATION (static wildcard): import static org.junit.jupiter.api.Assertions.* above

    public void check(java.util.List<String> names) {
        if (names.isEmpty()) {
            return;
        }
    }
}
