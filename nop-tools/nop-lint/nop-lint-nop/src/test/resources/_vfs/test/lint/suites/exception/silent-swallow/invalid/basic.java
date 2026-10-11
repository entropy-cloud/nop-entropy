package demo;

/**
 * Invalid catch blocks: each is a silent swallow (no good signal inside the
 * body). mjs check-silent-swallow fixture semantics: violation, plus the
 * comment-bypass and log-then-continue shapes the gate must keep catching.
 */
class Basic {

    // mjs fixture 'violation': log + return, no signal
    void violation() {
        try {
            doSomething();
        } catch (Exception e) {
            LOG.warn("ignored", e);
            return null;
        }
    }

    // log-and-continue only
    void logAndContinue() {
        try {
            doSomething();
        } catch (Exception e) {
            LOG.warn("ignored", e);
        }
    }
}
