package io.nop.ai.core.model;

import io.nop.ai.core.model._gen._PromptOutputModel;
import io.nop.api.core.util.INeedInit;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.parse.XDefinitionParser;
import io.nop.xlang.xmeta.SchemaLoader;

public class PromptOutputModel extends _PromptOutputModel implements INeedInit {
    private IXDefinition xdefObj;

    public PromptOutputModel() {

    }

    @Override
    public void init() {
        if (getXdef() != null) {
            xdefObj = new XDefinitionParser().allowUnknownStdDomain(true).parseFromNode(getXdef());
        } else if (getXdefPath() != null) {
            xdefObj = SchemaLoader.loadXDefinition(getXdefPath());
        }
    }

    public IXDefinition getXdefObj() {
        return xdefObj;
    }

}
