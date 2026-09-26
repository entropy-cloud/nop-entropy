package io.nop.refactor.core.operation;

import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.refactor.core.NonApply;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The plan segment's product (plan 09 adjudication 1): the per-file edit
 * plan plus every non-applied entry pre-collected on the way — nothing has
 * been written when this exists. The framework's single apply point lands
 * {@link #files()} through the WI4 entry; the single verify point assembles
 * the payload through the WI5 verifier, resolving each edited file's
 * language through {@link #languageByPath()} and reusing {@link #engine()}
 * for any residual subset.
 */
public record OperationPlan(List<PlannedFile> files,
                            Map<String, LintLanguage> languageByPath,
                            LintEngine engine,
                            List<NonApply> nonApplies) {

    public OperationPlan {
        Objects.requireNonNull(files, "files must not be null");
        files = List.copyOf(files);
        Objects.requireNonNull(languageByPath, "languageByPath must not be null");
        languageByPath = Map.copyOf(languageByPath);
        Objects.requireNonNull(engine, "engine must not be null");
        Objects.requireNonNull(nonApplies, "nonApplies must not be null");
        nonApplies = List.copyOf(nonApplies);
    }
}
