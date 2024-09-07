package io.nop.core.resource.deps;

import io.nop.core.resource.component.IResourceDependencyManager;

import java.util.HashSet;
import java.util.Set;

public class ResourceDependsHelper {
    public static String dumpDependsSet(ResourceDependencySet deps, IResourceDependencyManager manager) {
        StringBuilder sb = new StringBuilder();
        _dump(sb, deps, manager, new HashSet<>(), 0);
        return sb.toString();
    }

    static private void _dump(StringBuilder sb, ResourceDependencySet deps, IResourceDependencyManager manager,
                              Set<String> visited, int level) {
        for (String dep : deps.getDepends()) {
            indent(sb, level);
            sb.append(dep);

            if (visited.add(dep)) {
                ResourceDependencySet sub = manager.getResourceDepends(dep);
                if (sub != null) {
                    _dump(sb, sub, manager, visited, level + 1);
                }
            } else {
                sb.append('*');
            }
        }
    }

    static void indent(StringBuilder sb, int level) {
        sb.append('\n');
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }
}
