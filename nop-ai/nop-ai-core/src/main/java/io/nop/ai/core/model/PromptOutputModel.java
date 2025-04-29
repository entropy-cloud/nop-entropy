package io.nop.ai.core.model;

import io.nop.ai.core.model._gen._PromptOutputModel;
import io.nop.ai.core.xdef.AiXDefHelper;
import io.nop.api.core.util.INeedInit;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.action.IActionOutputModel;
import io.nop.xlang.xmeta.SchemaLoader;

public class PromptOutputModel extends _PromptOutputModel implements INeedInit, IActionOutputModel {
    private IXDefinition xdefObj;
    private String xdefForAi;

    public PromptOutputModel() {

    }

    @Override
    public void init() {
        if (getXdefPath() != null) {
            xdefObj = SchemaLoader.loadXDefinition(getXdefPath());
        }

        if (xdefObj != null) {
            this.xdefForAi = AiXDefHelper.transformForAi(xdefObj.toNode()).xml();
        }
    }

    public IXDefinition getXdefObj() {
        return xdefObj;
    }

    public String getXdefForAi() {
        return xdefForAi;
    }
}
