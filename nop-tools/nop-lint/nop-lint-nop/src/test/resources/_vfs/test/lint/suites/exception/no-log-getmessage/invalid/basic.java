package demo;

/**
 * Invalid catch blocks: getMessage is the only trace-bearing use and the
 * catch does not rethrow (ast-grep java-lint-getmessage-only semantics).
 */
class Basic {

    // the canonical shape from the ast-grep rule header
    void logMessageOnly() {
        try {
            doSomething();
        } catch (Exception e) {
            LOG.error("Failed: {}", e.getMessage());
        }
    }

    // warn variant
    void warnMessageOnly() {
        try {
            doSomething();
        } catch (Exception e) {
            LOG.warn("failed with " + e.getMessage());
        }
    }
}
