package io.nop.refactor.core;

import io.nop.lint.core.node.SourceRange;

/**
 * One structured per-file edit (baseline §四 {@code edits}: "path + 范围 +
 * 变更摘要"). Value semantics only — the application machinery is the
 * EditPlanApplier entry this record reports on.
 *
 * @param path    the edited file's display path; never blank
 * @param range   the byte range the edit replaced in the pre-edit content
 * @param summary the edit's change summary — taken from the edit carrier's
 *                description field (WI5 adjudication: {@code Fix.description});
 *                never blank
 */
public record FileEdit(String path, SourceRange range, String summary) {

    public FileEdit {
        if (path == null || path.isBlank())
            throw new NopRefactorException("FileEdit.path must be non-blank (fail-closed)");
        if (range == null)
            throw new NopRefactorException("FileEdit.range must not be null for '" + path
                    + "' (fail-closed)");
        if (summary == null || summary.isBlank())
            throw new NopRefactorException("FileEdit.summary must be non-blank for '" + path
                    + "' (fail-closed)");
    }
}
