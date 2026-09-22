package demo;

import java.io.IOException;

/**
 * Compliant catch blocks: each carries at least one good signal inside its
 * body (mjs check-silent-swallow fixture semantics: violation / compliant /
 * errors-enum / throw / string-url / javadoc-catch). Parse-only fixtures:
 * the surrounding identifiers need no declarations.
 */
class Clean {

    // mjs fixture 'compliant': wrap with an ErrorCode-bearing exception
    void compliant() {
        try {
            doSomething();
        } catch (Exception e) {
            throw new NopMetadataException(NopMetadataErrors.ERR_TEST, e);
        }
    }

    // mjs fixture 'throw': plain rethrow
    void rethrows() {
        try {
            doSomething();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // mjs fixture 'errors-enum': an Errors-reference inside the body counts
    // as a signal (exact identifier form: Errors.<field>)
    void errorsEnum() {
        try {
            doSomething();
        } catch (Exception e) {
            log(Errors.ERR_INDEX_BUILD_FAILED + ": " + e.getMessage());
        }
    }

    // the mjs errors-enum fixture's exact shape: NopMetadataErrors.<field>
    // read inside a message-building expression counts as a signal
    void nopMetadataErrorsEnum() {
        try {
            doSomething();
        } catch (Exception e) {
            result.setErrors(List.of(NopMetadataErrors.ERR_INDEX_BUILD_FAILED
                    + ": " + e.getMessage()));
        }
    }

    // ErrorCode via a static helper call
    void errorCodeCall() {
        try {
            doSomething();
        } catch (Exception e) {
            report(ErrorCode.of("DEMO"), e);
        }
    }

    // Biz.fatal( signal
    void bizFatal() {
        try {
            doSomething();
        } catch (Exception e) {
            Biz.fatal(e);
        }
    }

    // mjs fixture 'string-url': a "//" inside a string literal must not
    // mask the real throw signal; and a signal-bearing comment is harmless
    void stringUrlThenThrow() {
        try {
            doSomething();
        } catch (Exception e) {
            // ErrorCode. in this comment does not matter: the throw below is real
            LOG.warn("see http://example.com for details");
            throw new RuntimeException(e);
        }
    }

    // mjs fixture 'javadoc-catch': `catch (SQLException)` appearing only in
    // a javadoc block must not create a phantom catch block; the real catch
    // below carries a signal
    /**
     * AR-06: catch (SQLException) handling distinguishes two failures.
     */
    void javadocCatch() {
        try {
            doSomething();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Signal keywords appear only inside a comment; the real signal is the
    // rethrow below. Comments can never carry an AST signal (kind isolation).
    void commentOnlyMentions() {
        try {
            doSomething();
        } catch (Exception e) {
            // BizException NopMetadataException( .errorCode( Errors.
            LOG.info("handled elsewhere");
            throw new RuntimeException(e);
        }
    }
}
