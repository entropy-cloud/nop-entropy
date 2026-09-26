package io.nop.refactor.core.operation;

import io.nop.lint.core.fix.Fix;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.symbol.RenameResolution;
import io.nop.refactor.core.symbol.SymbolResolverAdapter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rename operation's first rung (roadmap WI10, plan 10): locals and
 * parameters, single file. It rides the same framework lifecycle as the
 * codemod face — check validates the input shape, plan asks the injected
 * adapter for the four-state rename resolution and translates it into the
 * framework's plan vocabulary (rewrite ranges become {@code Fix} edits; the
 * three refusals become zero-edit plans with structured {@code NonApply}
 * entries), and apply/verify are inherited unchanged from the framework's
 * single path. The pre-computed symbol-intact assertion rides the plan into
 * the single assemble point.
 */
public final class RenameOperation implements RefactorOperation<RenameRequest> {

    /**
     * Stateless singleton, like the codemod operation — one framework, no
     * per-run state.
     */
    public static final RenameOperation INSTANCE = new RenameOperation();

    private RenameOperation() {
    }

    /**
     * Segment 1: the input shape gate. The request record validates name,
     * scope, files and injections; here the first rung's own boundaries:
     * the locator must be the file + byte offset form (FQN targeting is the
     * WI11 type face) and the target file must be part of the module the
     * request carries.
     */
    @Override
    public void check(RenameRequest input) {
        if (input.target().isFqnForm()) {
            throw new NopRefactorException("the first rename rung locates by file + "
                    + "byte offset; FQN targeting (types) lands in WI11 (got '"
                    + input.target().fqn() + "'; fail-closed)");
        }
        boolean pathInModule = input.files().stream()
                .anyMatch(file -> file.path().equals(input.target().path()));
        if (!pathInModule) {
            throw new NopRefactorException("the rename target file '"
                    + input.target().path() + "' is not part of the request's module "
                    + "file set (fail-closed: a rename outside its own module face is "
                    + "a wiring bug, not an empty result)");
        }
    }

    /**
     * Segment 2: the four-state translation (plan 10 adjudications 2/4/5/6).
     * RESOLVED produces the rewrite-range edit list — the declaration's own
     * identifier plus every bound reference, each carrying the old span and
     * the new name — together with the pre-computed symbol-intact assertion;
     * every refusal produces a zero-edit plan whose structured NonApply
     * entry names the reason and the context.
     */
    @Override
    public OperationPlan plan(RenameRequest input) {
        SymbolResolverAdapter.DeclarationIndex index =
                input.resolver().buildIndex(input.files());
        RenameResolution resolution = input.resolver().renameResolution(index,
                input.target(), input.newName());

        if (resolution.state() != RenameResolution.State.RESOLVED) {
            NonApply.Reason reason = switch (resolution.state()) {
                case CONFLICT -> NonApply.Reason.CONFLICT;
                case OUT_OF_SCOPE -> NonApply.Reason.OUT_OF_SCOPE;
                case UNRESOLVED -> NonApply.Reason.UNRESOLVED_TARGET;
                default -> throw new NopRefactorException("unreachable rename state: "
                        + resolution.state());
            };
            NonApply nonApply = new NonApply(reason, input.target().path(),
                    resolution.detail());
            return new OperationPlan(List.of(), Map.of(), input.engine(),
                    List.of(nonApply), null);
        }

        String description = "rename " + resolution.declaration().name()
                + " to " + input.newName();
        List<Fix> edits = new ArrayList<>(resolution.occurrences().size());
        for (var range : resolution.occurrences()) {
            edits.add(new Fix(range, input.newName(), "rename", description, 0));
        }
        Map<String, io.nop.lint.core.lang.LintLanguage> languageByPath =
                new LinkedHashMap<>();
        languageByPath.put(input.target().path(), input.language());

        byte[] original = input.files().stream()
                .filter(file -> file.path().equals(input.target().path()))
                .findFirst()
                .orElseThrow(() -> new NopRefactorException("the target file '"
                        + input.target().path() + "' vanished from the module set "
                        + "(checked in segment 1; fail-closed)"))
                .content()
                .getBytes(StandardCharsets.UTF_8);

        PlannedFile planned = new PlannedFile(
                java.nio.file.Path.of(input.target().path()), original, edits,
                input.language());
        return new OperationPlan(List.of(planned), languageByPath, input.engine(),
                List.of(), resolution.symbolIntact());
    }
}
