package io.nop.ai.core.model;

import io.nop.ai.core.model._gen._PromptOutputModel;
import io.nop.ai.core.xdef.AiXDefHelper;
import io.nop.api.core.util.INeedInit;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.parse.XDefinitionParser;
import io.nop.xlang.xmeta.SchemaLoader;

public class PromptOutputModel extends _PromptOutputModel implements INeedInit {
    private IXDefinition xdefObj;
    private String xdefForAi;

    public PromptOutputModel() {

    }

    @Override
    public void init() {
        if (!StringHelper.isEmpty(getXdef())) {
            XNode xdef = XNodeParser.instance().parseFromText(null, getXdef());
            xdefObj = new XDefinitionParser().allowUnknownStdDomain(true).parseFromNode(xdef);
        } else if (getXdefPath() != null) {
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
