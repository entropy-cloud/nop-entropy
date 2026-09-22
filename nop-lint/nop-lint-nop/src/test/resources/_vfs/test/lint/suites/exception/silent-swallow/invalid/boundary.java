package demo;

/**
 * Boundary shapes for silent-swallow (mjs fixture 'comment-bypass' family):
 * signals appear ONLY inside comments or strings, so the catch remains a
 * silent swallow; a signal in the catch header's parameter type does not
 * exempt the body (the mjs gate scans the brace span only, field: body).
 */
class Boundary {

    // mjs fixture 'comment-bypass': the ErrorCode. mention is inside a
    // comment — it must NOT count as a good signal
    void commentBypass() {
        try {
            doSomething();
        } catch (Exception e) {
            // ErrorCode. in comment should NOT count
            LOG.warn("ignored", e);
        }
    }

    // every signal keyword inside one comment still swallows
    void allSignalsInComment() {
        try {
            doSomething();
        } catch (Exception e) {
            // throw new NopMetadataException( x.errorCode( ErrorCode. Errors. new BizException( Biz.fatal(
            LOG.info("swallowed");
        }
    }

    // a signal keyword inside a string literal does not exempt the body
    // (AST delta vs the text-level mjs gate, adjudicated in the plan)
    void signalInString() {
        try {
            doSomething();
        } catch (Exception e) {
            LOG.warn("rethrow with BizException when handled", e);
        }
    }

    // the exception TYPE in the catch header (BizException) sits outside the
    // body block; field: body must not treat it as a signal
    void headerTypeIsNotASignal() {
        try {
            doSomething();
        } catch (BizException e) {
            LOG.warn("ignored", e);
        }
    }

    // empty catch body: the original empty-catch shape, also a silent swallow
    void emptyBody() {
        try {
            doSomething();
        } catch (Exception e) {
        }
    }
}
