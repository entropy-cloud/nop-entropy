package io.nop.lint.core.fix;

import io.nop.lint.core.node.SourceRange;

/**
 * One concrete rewrite produced for a match (roadmap item 25, design 04 §7):
 * replace the byte range with the rendered text. Self-sufficient — the
 * applier needs no node structure — and deterministic: the priority that
 * resolved any conflict is already reflected in which fixes survive
 * {@link Fixer#merge}.
 *
 * @param order the generation ordinal (ruleset declaration order, then match
 *              order) that broke conflicts during the merge
 */
public record Fix(SourceRange range, String replacement, String ruleId, String description,
                  int order) {
}
