package io.nop.maven.plugin.shaded;

import org.apache.maven.plugins.shade.relocation.Relocator;

import java.util.List;

public class ShadeHelper {
    public static String relocate(String name, List<Relocator> relocatorList) {
        for (Relocator relocator : relocatorList) {
            if (!relocator.canRelocateClass(name))
                continue;

            String relocated = relocator.relocateClass(name);
            if (relocated != null)
                return relocated;
        }
        return name;
    }
}
