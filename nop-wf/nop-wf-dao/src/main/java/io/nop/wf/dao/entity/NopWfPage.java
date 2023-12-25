package io.nop.wf.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.util.ProcessResult;
import io.nop.wf.dao.NopWfDaoConstants;
import io.nop.wf.dao.entity._gen._NopWfPage;


@BizObjName("NopWfPage")
public class NopWfPage extends _NopWfPage {
    public NopWfPage() {
    }

    @Override
    public ProcessResult orm_preSave() {
        checkContent();
        return ProcessResult.CONTINUE;
    }

    @Override
    public ProcessResult orm_preUpdate() {
        checkContent();
        return ProcessResult.CONTINUE;
    }

    protected void checkContent() {
        getPageContentComponent().require_jsonMap().put(NopWfDaoConstants.PROP_XUI_SCHEMA_TYPE, getPageSchemaType());
    }
}
