package io.nop.lint.core.pattern;

import io.nop.lint.core.node.LintNode;

/**
 * Adapts a compiled {@link SourcePattern} to the node-level matcher surface:
 * matches one candidate node against the pattern root under SMART
 * strictness, with the kind prefilter applied first.
 *
 * <p>Bindings are probed into a cloned environment and committed only on
 * success, so a failed pattern match never leaks captures.</p>
 */
public final class PatternNodeMatcher implements NodeMatcher {

    private final SourcePattern pattern;

    public PatternNodeMatcher(SourcePattern pattern) {
        this.pattern = pattern;
    }

    public SourcePattern pattern() {
        return pattern;
    }

    @Override
    public boolean matches(LintNode node, MetaVarEnv env) {
        if (!pattern.mayMatchKind(node.kindId())) {
            return false;
        }
        MetaVarEnv probe = env.clone();
        if (PatternMatcher.matchRoot(pattern.root(), node, probe, Strictness.SMART)) {
            env.adopt(probe);
            return true;
        }
        return false;
    }
}
