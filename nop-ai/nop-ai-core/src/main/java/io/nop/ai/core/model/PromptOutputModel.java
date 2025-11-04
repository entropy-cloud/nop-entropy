package io.nop.ai.core.model;

import io.nop.ai.core.model._gen._PromptOutputModel;
import io.nop.ai.core.xdef.AiXDefHelper;
import io.nop.api.core.util.INeedInit;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.markdown.MarkdownConstants;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.action.IActionOutputModel;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.List;

public class PromptOutputModel extends _PromptOutputModel implements INeedInit, IActionOutputModel {
    private IXDefinition xdefObj;
    private String xdefForAi;

    private MarkdownDocument markdownTpl;
    private MarkdownDocument markdownTplWithoutDetail;

    public PromptOutputModel() {

    }

    @Override
    public void init() {
        if (getXdefPath() != null) {
            xdefObj = SchemaLoader.loadXDefinition(getXdefPath());
        }

        if (getXdefPath() != null) {
            this.xdefForAi = AiXDefHelper.loadXDefForAi(getXdefPath()).xml();
        }

        if (getMarkdownPath() != null) {
            markdownTpl = (MarkdownDocument) ResourceComponentManager.instance().loadComponentModel(getMarkdownPath());
        }
    }

    public IXDefinition getXdefObj() {
        return xdefObj;
    }

    public String getXdefForAi() {
        return xdefForAi;
    }

    public MarkdownDocument getMarkdownTpl() {
        return markdownTpl;
    }

    public MarkdownDocument getMarkdownTplWithoutDetail() {
        if (markdownTplWithoutDetail == null) {
            if (markdownTpl != null) {
                MarkdownDocument doc = markdownTpl.cloneInstance();
                doc.removeSectionWithTag(MarkdownConstants.TAG_DETAIL);
                markdownTplWithoutDetail = doc;
            }
        }
        return markdownTplWithoutDetail;
    }

    public List<String> getMarkdownTitles() {
        if (markdownTpl == null)
            return null;
        return markdownTpl.getAllFullTitles();
    }
}
