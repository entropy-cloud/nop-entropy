package io.nop.web.page.graph_designer.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.web.page.graph_designer.model._gen._GraphDesignerModel;
import io.nop.xui.vue.VueNodeChecker;

import static io.nop.xui.vue.VueErrors.ARG_COMPONENT_NAME;
import static io.nop.xui.vue.VueErrors.ERR_VUE_DUPLICATE_COMPONENT_NAME;

public class GraphDesignerModel extends _GraphDesignerModel implements INeedInit {
    public GraphDesignerModel() {

    }

    @Override
    public void init() {
        VueNodeChecker checker = new VueNodeChecker();
        checker.addImportTypes(keySet_imports());
        for (String name : keySet_styleComponents()) {
            if (!checker.addImportType(name))
                throw new NopException(ERR_VUE_DUPLICATE_COMPONENT_NAME)
                        .source(getStyleComponent(name))
                        .param(ARG_COMPONENT_NAME, name);
        }

        for (String name : keySet_components()) {
            if (!checker.addImportType(name))
                throw new NopException(ERR_VUE_DUPLICATE_COMPONENT_NAME)
                        .source(getComponent(name))
                        .param(ARG_COMPONENT_NAME, name);
        }

        if (this.getNodes() != null) {
            this.getNodes().forEach(node -> {
                if (node.getTemplate() != null)
                    checker.check(node.getTemplate());
            });
        }
    }
}
