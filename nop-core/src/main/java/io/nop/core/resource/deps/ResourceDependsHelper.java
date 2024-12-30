package io.nop.core.resource.deps;

import java.util.HashSet;
import java.util.Set;

public class ResourceDependsHelper {
    public static String dumpDependsSet(ResourceDependencySet deps) {
        StringBuilder sb = new StringBuilder();
        _dump(sb, deps, new HashSet<>(), 0);
        return sb.toString();
    }

    static private void _dump(StringBuilder sb, ResourceDependencySet deps,
                              Set<String> visited, int level) {
        for (ResourceDependencySet depSet : deps.getDepends()) {
            String dep = depSet.getResourcePath();
            indent(sb, level);
            sb.append(dep);

            if (visited.add(dep)) {
                _dump(sb, depSet, visited, level + 1);
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
