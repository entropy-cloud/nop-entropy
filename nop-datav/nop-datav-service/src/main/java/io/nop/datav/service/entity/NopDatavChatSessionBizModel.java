
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.crud.CrudBizModel;

import io.nop.core.context.IServiceContext;
import io.nop.datav.biz.INopDatavChatSessionBiz;
import io.nop.datav.dao.entity.NopDatavChatMessage;
import io.nop.datav.dao.entity.NopDatavChatSession;

import java.util.Set;
import java.util.function.BiConsumer;

/**
 * ChatBI 会话 BizModel（D6-1 follow-up）。
 *
 * <p>P1-09（plan 2026-08-15-2146-3 Phase 3）：标准 {@code delete(id)} / {@code batchDelete} /
 * {@code deleteByQuery} 均虚分派到 {@code doDeleteEntity}，覆写级联物理删除 ChatMessage 子行——
 * 收敛标准 {@code __delete} 与自定义 {@code deleteChatSession}（经 ChatBiSessionManager）的删除语义
 * 为单一路径（两条路径都不留孤儿消息）。消息级联复用 {@code ChatBiSessionManager.deleteSession}
 * 的 deleteByQuery 逻辑片段；<b>不整体调用</b>后者——其 {@code requireSession} owner 校验与
 * session 行自删不适用于 admin 标准路径（双重删除 + 把 owner-scope 校验注入标准删除）。
 * 失败异常传播（无静默跳过）。</p>
 */
@BizModel("NopDatavChatSession")
public class NopDatavChatSessionBizModel extends CrudBizModel<NopDatavChatSession> implements INopDatavChatSessionBiz{
    public NopDatavChatSessionBizModel(){
        setEntityName(NopDatavChatSession.class.getName());
    }

    @Override
    protected void doDeleteEntity(@Name("entity") NopDatavChatSession entity,
                                  @Name("refNamesToCheck") Set<String> refNamesToCheck,
                                  @Name("prepareDelete") BiConsumer<NopDatavChatSession, IServiceContext> prepareDelete,
                                  IServiceContext context) {
        super.doDeleteEntity(entity, refNamesToCheck, prepareDelete, context);
        // P1-09：级联物理删除消息（镜像 ChatBiSessionManager.deleteSession 的消息级联片段）
        QueryBean msgQuery = new QueryBean();
        msgQuery.addFilter(FilterBeans.eq("sessionId", entity.getSessionId()));
        daoProvider().daoFor(NopDatavChatMessage.class).deleteByQuery(msgQuery);
    }
}
