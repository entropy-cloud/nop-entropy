package demo;

/**
 * Compliant catch blocks for no-log-getmessage: the exception object (not
 * only its message) reaches the logger, or the catch rethrows.
 */
class Clean {

    // full stack trace preserved: only the exception object is logged.
    // (Note: the ast-grep original's "Good" example LOG.error(msg,
    // e.getMessage(), e) is still flagged by that rule's own matchers —
    // only a rethrow exempts; nop-lint keeps the identical semantics and
    // records the imprecision in the plan comparison table.)
    void logsFullException() {
        try {
            doSomething();
        } catch (Exception e) {
            LOG.error("Failed: {}", e);
        }
    }

    // rethrow exempt: getMessage use plus a throw in the same catch
    void getMessageThenRethrow() {
        try {
            doSomething();
        } catch (Exception e) {
            LOG.warn("retrying after {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // no getMessage at all
    void noMessageUse() {
        try {
            doSomething();
        } catch (Exception e) {
            LOG.error("Failed", e);
        }
    }

    // getMessage use outside any catch clause is not this rule's concern
    void messageOutsideCatch() {
        String label = config.getMessage();
        LOG.info(label);
    }
}
