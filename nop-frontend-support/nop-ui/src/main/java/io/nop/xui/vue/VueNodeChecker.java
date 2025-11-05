package io.nop.xui.vue;

import io.nop.api.core.exceptions.NopException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static io.nop.xui.vue.VueErrors.ARG_TYPE;
import static io.nop.xui.vue.VueErrors.ERR_VUE_INVALID_NODE_TYPE;

public class VueNodeChecker {
    private Set<String> importTypes = new HashSet<>();

    public void addImportTypes(Collection<String> types) {
        if (types != null) {
            importTypes.addAll(types);
        }
    }

    public boolean containsImportType(String importType) {
        return importTypes.contains(importType);
    }

    public boolean addImportType(String importType) {
        return importTypes.add(importType);
    }

    public Set<String> getImportTypes() {
        return importTypes;
    }

    public void check(VueNode node) {
        node.forEachNode(child -> {
            String type = child.getType();
            if (Character.isUpperCase(type.charAt(0)) || type.indexOf('-') > 0) {
                if (!importTypes.contains(type))
                    throw new NopException(ERR_VUE_INVALID_NODE_TYPE).source(child)
                            .param(ARG_TYPE, type);
            }
        });
    }
}
