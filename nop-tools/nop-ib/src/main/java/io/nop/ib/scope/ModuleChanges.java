package io.nop.ib.scope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classified change buckets per module plus module-less changes (design 01 §七
 * routing input).
 */
public class ModuleChanges {
    private final Map<String, List<String>> byModuleAndKind = new LinkedHashMap<>();
    private final List<String> outsideModules = new ArrayList<>();

    public void add(String moduleArtifactId, ChangeClassifier.Kind kind, String relPath) {
        if (moduleArtifactId == null) {
            outsideModules.add(relPath);
            return;
        }
        byModuleAndKind.computeIfAbsent(moduleArtifactId + "#" + kind, k -> new ArrayList<>())
                .add(relPath);
    }

    public List<String> pathsOf(String module, ChangeClassifier.Kind kind) {
        return byModuleAndKind.getOrDefault(module + "#" + kind, List.of());
    }

    public boolean hasAny(String module, ChangeClassifier.Kind kind) {
        return !pathsOf(module, kind).isEmpty();
    }

    public List<String> outsideModules() {
        return outsideModules;
    }

    public Map<String, List<String>> all() {
        return byModuleAndKind;
    }
}
