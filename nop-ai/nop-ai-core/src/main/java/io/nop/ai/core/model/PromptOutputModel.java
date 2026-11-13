package io.nop.ai.core.model;

import io.nop.ai.core.model._gen._PromptOutputModel;
import io.nop.ai.core.api.xdef.AiXDefHelper;
import io.nop.api.core.util.INeedInit;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.markdown.MarkdownConstants;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.action.IActionOutputModel;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.List;

public class PromptOutputModel extends _PromptOutputModel implements INeedInit, IActionOutputModel {
    // 3.1-MA-13（plan 2555 裁定 C / C1-002 惰性 clone 发布面）：init() 在装载临界区内
    // 一次性写入，volatile 保证跨线程发布；markdownTplWithoutDetail 为惰性 clone 派生面
    // （休眠：全库 main 无调用方），volatile 化 + 构造后只读契约（除惰性 clone 一次性写入）
    private volatile IXDefinition xdefObj;
    private volatile String xdefForAi;

    private volatile MarkdownDocument markdownTpl;
    private volatile MarkdownDocument markdownTplWithoutDetail;

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
        // 3.1-MA-13（plan 2555 裁定 C）：惰性 clone 单次发布——重复 clone 仅冗余无害，
        // 发布后的文档对调用方只读（removeSectionWithTag 仅发生在发布前的私有副本上）
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
