package io.nop.ai.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.ai.dao._NopAiDaoConstants;
import io.nop.ai.dao.entity._gen._NopAiSession;


@BizObjName("NopAiSession")
public class NopAiSession extends _NopAiSession{

    /** 稳定领域判定（审计 AI-12a 下沉）：会话处于可再入的空闲态。 */
    public boolean isIdle() {
        Integer s = getStatus();
        return s != null && s == _NopAiDaoConstants.SESSION_STATUS_IDLE;
    }

    /** 稳定领域判定（审计 AI-12a 下沉）：会话处于终态（completed/failed/stopped）。 */
    public boolean isTerminal() {
        Integer s = getStatus();
        return s != null && (s == _NopAiDaoConstants.SESSION_STATUS_COMPLETED
                || s == _NopAiDaoConstants.SESSION_STATUS_FAILED
                || s == _NopAiDaoConstants.SESSION_STATUS_STOPPED);
    }



}
