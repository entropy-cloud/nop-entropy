package io.nop.ib.scope;

import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave6 6A-1 F13 pin: the scope layer (module graph reverse closure) had zero
 * direct tests — the narrow-selection invariant that guards test selection
 * correctness is now pinned on a synthetic reactor.
 */
class TestModuleGraphClosure {

    private IbModel threeLevelReactor() {
        IbModel model = new IbModel();
        model.setRepoRoot("/repo");
        model.addModule(module("root", null));
        model.addModule(module("a", "root"));
        model.addModule(module("b", "root"));
        model.addModule(module("a-lib", "a"));
        model.addModule(module("a-app", "a"));
        // a-app depends on a-lib
        IbModule.DepEdge edge = new IbModule.DepEdge();
        edge.setGa("g:a-lib");
        model.module("a-app").getDependencies().add(edge);
        for (IbModule m : model.getModules())
            model.getModulesByArtifactId().put(m.getArtifactId(), m);
        return model;
    }

    private IbModule module(String artifactId, String parentArtifact) {
        IbModule m = new IbModule();
        m.setGroupId("g");
        m.setArtifactId(artifactId);
        m.setVersion("1");
        if (parentArtifact != null)
            m.setParentGa("g:" + parentArtifact);
        m.setBasedir(artifactId);
        return m;
    }

    /** changing a-lib must widen to its dependent a-app (reverse dep edge). */
    @Test
    void reverseClosureFollowsDependencyEdges() {
        ModuleGraph graph = new ModuleGraph(threeLevelReactor());
        List<String> closure = graph.reverseClosure(List.of("a-lib"));
        assertTrue(closure.contains("a-app"), "dependent module must be in the closure");
        assertTrue(closure.contains("a-lib"), "seed stays in its own closure");
    }

    /** a pom change in the parent propagates to children (pomPropagation). */
    @Test
    void pomPropagationReachesChildren() {
        ModuleGraph graph = new ModuleGraph(threeLevelReactor());
        List<String> affected = graph.pomPropagation(List.of("root"));
        assertTrue(affected.contains("a") && affected.contains("b"),
                "parent pom change must reach direct children, got " + affected);
    }
}
