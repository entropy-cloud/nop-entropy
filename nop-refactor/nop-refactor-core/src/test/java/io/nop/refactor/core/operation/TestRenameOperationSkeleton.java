package io.nop.refactor.core.operation;

import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.symbol.SymbolResolverAdapter.SymbolTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The rename skeleton's lifecycle proof (plan 09 adjudications 5/6): the
 * skeleton rides the same framework template as the codemod operation —
 * check validates the input triple, plan fails closed with the exact pinned
 * marker — and the runner is the mechanism that drives both segments, so
 * when WI10/WI11 land the resolution the rename inherits apply/verify with
 * no second path.
 */
public class TestRenameOperationSkeleton {

    private static RenameRequest validRequest() {
        return new RenameRequest(SymbolTarget.ofFqn("a.Service"), "freshName",
                RenameScope.MODULE);
    }

    @Test
    void checkAcceptsAWellFormedTriple() {
        RenameOperation.INSTANCE.check(validRequest());
    }

    @Test
    void checkAndConstructionRejectMalformedInput() {
        // the locator form is fail-closed in SymbolTarget itself: neither form
        assertThrows(NopRefactorException.class,
                () -> new SymbolTarget(null, null, null));
        // both forms at once is just as malformed
        assertThrows(NopRefactorException.class,
                () -> new SymbolTarget("a.Service", "a/Service.java", 0L));
        // a blank or non-identifier new name
        assertThrows(NopRefactorException.class,
                () -> new RenameRequest(SymbolTarget.ofFqn("a.Service"), null,
                        RenameScope.MODULE));
        assertThrows(NopRefactorException.class,
                () -> new RenameRequest(SymbolTarget.ofFqn("a.Service"), "not a name",
                        RenameScope.MODULE));
        // the v1 domain is module-scoped only
        assertThrows(NopRefactorException.class,
                () -> new RenameRequest(SymbolTarget.ofFqn("a.Service"), "freshName", null));
    }

    @Test
    void planFailsClosedWithTheExactPinnedMarker() {
        NopRefactorException thrown = assertThrows(NopRefactorException.class,
                () -> RefactorOperationRunner.INSTANCE.run(
                        RenameOperation.INSTANCE, validRequest(), true));
        // the platform exception model carries the plain string as the
        // error code (NopException's message renders the full envelope) —
        // the pinned contract lives there, exact and un-drifted
        assertEquals(RenameOperation.NOT_IMPLEMENTED_MESSAGE, thrown.getErrorCode(),
                "the runner drives check then plan — a drifted marker is a drifted contract");
    }
}
