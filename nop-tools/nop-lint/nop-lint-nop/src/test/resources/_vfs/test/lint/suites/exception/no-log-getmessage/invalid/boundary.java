package demo;

/**
 * Boundary shapes for no-log-getmessage: a throw mentioned only in a comment
 * cannot exempt (AST signals are structural), and the getMessage use is
 * found even nested inside a lambda (stopBy: end is a full DFS).
 */
class Boundary {

    void commentThrowDoesNotExempt() {
        try {
            doSomething();
        } catch (Exception e) {
            // throw new RuntimeException(e) in a comment is not a rethrow
            LOG.warn("failed with {}", e.getMessage());
        }
    }

    void deepInsideLambda() {
        try {
            doSomething();
        } catch (Exception e) {
            Runnable r = () -> LOG.debug("msg {}", e.getMessage());
            r.run();
        }
    }
}
