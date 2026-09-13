package io.nop.datav.service.chatbi;

import io.nop.api.core.time.CoreMetrics;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.datav.dao.entity.NopDatavChatMessage;
import io.nop.datav.dao.entity.NopDatavChatSession;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ARG_SESSION_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_USER_NAME;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_NOT_SESSION_OWNER;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_SESSION_NOT_FOUND;

/**
 * ChatBI 多轮会话管理器（裁定 S1–S4，ai-design.md §10）。
 *
 * <p>职责：会话 CRUD（经真实持久层 {@link IDaoProvider}，非内存假实现）+ 历史注入上下文构造
 * （裁定 S2：双上界取小、最老优先丢弃）+ 归属校验（裁定 S4：userName 语义）。
 * 消息顺序经 {@code seq} 序号列（每会话内严格递增，UK 兜底并发冲突显式失败）。</p>
 *
 * <p>消息角色常量与 ORM dict {@code datav/chat-msg-role} 对应（user/assistant）。
 * 失败轮次（循环抛异常）不落库——调用方仅在循环成功返回后调用 {@link #appendTurn}。</p>
 */
public class ChatBiSessionManager {

    /** 消息角色：用户消息（与 ORM dict datav/chat-msg-role 的 USER 选项值一致）。 */
    public static final String ROLE_USER = "user";

    /** 消息角色：assistant 最终答案消息（与 ORM dict datav/chat-msg-role 的 ASSISTANT 选项值一致）。 */
    public static final String ROLE_ASSISTANT = "assistant";

    /** 会话标题缺省时从首轮问题截断的长度上限。 */
    static final int TITLE_MAX_LEN = 100;

    @Inject
    protected IDaoProvider daoProvider;

    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    // ==================== 会话生命周期 ====================

    /**
     * 创建空会话（裁定 S3：显式 create 语义；sessionTitle 可空，首轮提问后回填）。
     */
    public NopDatavChatSession createSession(String sessionTitle, String operator) {
        Timestamp now = CoreMetrics.currentTimestamp();
        NopDatavChatSession session = sessionDao().newEntity();
        session.setUserName(operator);
        session.setSessionTitle(sessionTitle);
        session.setDelFlag((byte) 0);
        session.setVersion(0L);
        session.setCreatedBy(operator);
        session.setCreateTime(now);
        session.setUpdatedBy(operator);
        session.setUpdateTime(now);
        sessionDao().saveEntityDirectly(session);
        return session;
    }

    /**
     * 按标识加载会话并校验归属（裁定 S4）。
     * 不存在/已删除抛 {@code ERR_DATAV_CHATBI_SESSION_NOT_FOUND}；非本人抛
     * {@code ERR_DATAV_CHATBI_NOT_SESSION_OWNER}（无静默降级）。
     */
    public NopDatavChatSession requireSession(String sessionId, String operator) {
        NopDatavChatSession session = sessionDao().getEntityById(sessionId);
        if (session == null) {
            throw new NopException(ERR_DATAV_CHATBI_SESSION_NOT_FOUND).param(ARG_SESSION_ID, sessionId);
        }
        if (!session.getUserName().equals(operator)) {
            throw new NopException(ERR_DATAV_CHATBI_NOT_SESSION_OWNER)
                    .param(ARG_USER_NAME, operator).param(ARG_SESSION_ID, sessionId);
        }
        return session;
    }

    /**
     * 列出本人会话（按 updateTime 降序；裁定 S5 listChatSessions 的数据源）。
     */
    public List<NopDatavChatSession> listSessions(String operator) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userName", operator));
        query.addOrderField("updateTime", false);
        return sessionDao().findAllByQuery(query);
    }

    /**
     * 删除会话及其全部消息（物理删除，裁定 S4；删除后 requireSession/loadHistory 显式报错）。
     * 两级均经 deleteByQuery 批量物理删除（镜像 NopDatavScreenBizModel.deleteAllByScreen 级联先例，
     * 避免跨 ORM session 的实体归属问题）。归属校验先行（requireSession 显式抛错语义保持）。
     */
    public void deleteSession(String sessionId, String operator) {
        requireSession(sessionId, operator);
        QueryBean msgQuery = new QueryBean();
        msgQuery.addFilter(FilterBeans.eq("sessionId", sessionId));
        messageDao().deleteByQuery(msgQuery);

        QueryBean sessionQuery = new QueryBean();
        sessionQuery.addFilter(FilterBeans.eq("sessionId", sessionId));
        sessionDao().deleteByQuery(sessionQuery);
    }

    // ==================== 历史读写 ====================

    /**
     * 加载会话全部消息（按 seq 升序）。归属校验后调用（{@code requireSession} 先行）。
     */
    public List<NopDatavChatMessage> loadMessages(String sessionId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("sessionId", sessionId));
        query.addOrderField("seq", false);
        return daoProvider.daoFor(NopDatavChatMessage.class).findAllByQuery(query);
    }

    /**
     * 追加一轮对话（user 消息 seq=k + assistant 消息 seq=k+1）。
     *
     * <p>assistant 消息留存 answer 文本 + 结构化结果 JSON（columns/rows/iterations，裁定 S4 数据留存）。
     * seq 取当前会话 max(seq)+1（裁定 S4：序号列并发语义，UK 冲突显式失败）。
     * 会话 updateTime 刷新 + 首轮 sessionTitle 为空时以问题截断回填。</p>
     */
    public void appendTurn(NopDatavChatSession session, String question, ChatBiResult result, String operator) {
        int nextSeq = nextSeq(session.getSessionId());
        Timestamp now = CoreMetrics.currentTimestamp();

        NopDatavChatMessage userMsg = newMessage(session, nextSeq, ROLE_USER, question, null, operator, now);
        messageDao().saveEntityDirectly(userMsg);

        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("columns", result.getColumns());
        resultJson.put("rows", result.getRows());
        resultJson.put("iterations", result.getIterations());
        NopDatavChatMessage assistantMsg = newMessage(session, nextSeq + 1, ROLE_ASSISTANT,
                result.getAnswer(), JsonTool.serialize(resultJson, false), operator, now);
        messageDao().saveEntityDirectly(assistantMsg);

        if (session.getSessionTitle() == null || session.getSessionTitle().isEmpty()) {
            String title = question.length() > TITLE_MAX_LEN ? question.substring(0, TITLE_MAX_LEN) : question;
            session.setSessionTitle(title);
        }
        session.setUpdatedBy(operator);
        session.setUpdateTime(now);
        sessionDao().updateEntityDirectly(session);
    }

    // ==================== 注入上下文构造（裁定 S2） ====================

    /**
     * 按裁定 S2 构造历史注入上下文：
     * <ol>
     *   <li>轮数上界：取最近 {@code maxTurns} 轮（一轮 = 一条 user 消息起头的消息组）。</li>
     *   <li>字符预算：从最新消息向最老消息累计，加入下一条更老消息会超预算即停止（最老优先丢弃）；
     *       最新单条单独超预算时截断该条（保证至少一条历史存活）。</li>
     *   <li>assistant 注入内容 = answer 文本 + 紧凑 JSON 数据摘要（RESULT_JSON 非空时）。</li>
     * </ol>
     */
    public List<ChatMessage> buildHistoryContext(List<NopDatavChatMessage> messages, int maxTurns, int maxChars) {
        if (messages == null || messages.isEmpty() || maxTurns <= 0 || maxChars <= 0) {
            return null;
        }

        // 步骤 1：轮数上界——找到最近 maxTurns 轮的起始 user 消息下标
        int startIndex = 0;
        int userCount = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (ROLE_USER.equals(messages.get(i).getRole())) {
                userCount++;
                if (userCount >= maxTurns) {
                    startIndex = i;
                    break;
                }
            }
        }

        // 步骤 2：字符预算——从最新向最老累计，超预算即停（最老优先丢弃）
        List<String> contents = new ArrayList<>();
        List<NopDatavChatMessage> kept = new ArrayList<>();
        int total = 0;
        for (int i = messages.size() - 1; i >= startIndex; i--) {
            NopDatavChatMessage msg = messages.get(i);
            String content = renderContent(msg);
            if (kept.isEmpty() && total + content.length() > maxChars) {
                // 最新一条单独超预算：截断该条（保证至少一条历史存活）
                content = content.substring(0, maxChars);
                contents.add(content);
                kept.add(msg);
                total = maxChars;
                break;
            }
            if (total + content.length() > maxChars) {
                break;
            }
            total += content.length();
            contents.add(content);
            kept.add(msg);
        }

        // 步骤 3：按时间正序还原为 ChatMessage（user → ChatUserMessage，assistant → ChatAssistantMessage）
        // kept/content 为 newest→oldest 平行数组（下标 i 对应同一条消息）
        List<ChatMessage> result = new ArrayList<>(kept.size());
        for (int i = kept.size() - 1; i >= 0; i--) {
            NopDatavChatMessage msg = kept.get(i);
            String content = contents.get(i);
            if (ROLE_USER.equals(msg.getRole())) {
                result.add(new ChatUserMessage(content));
            } else {
                result.add(new ChatAssistantMessage(content));
            }
        }
        return result;
    }

    /**
     * assistant 消息注入内容 = answer 文本 + 紧凑 JSON 数据摘要；user 消息即问题文本。
     * columns/rows 均为空时跳过数据摘要（空壳 JSON 无引用价值，避免污染上下文）。
     */
    private String renderContent(NopDatavChatMessage msg) {
        if (ROLE_ASSISTANT.equals(msg.getRole()) && msg.getResultJson() != null && !msg.getResultJson().isEmpty()) {
            try {
                Object parsed = JsonTool.parseNonStrict(msg.getResultJson());
                if (parsed instanceof Map) {
                    Map<?, ?> json = (Map<?, ?>) parsed;
                    boolean hasColumns = json.get("columns") instanceof List && !((List<?>) json.get("columns")).isEmpty();
                    boolean hasRows = json.get("rows") instanceof List && !((List<?>) json.get("rows")).isEmpty();
                    if (hasColumns || hasRows) {
                        return msg.getContent() + "\n[Result data] " + msg.getResultJson();
                    }
                }
            } catch (Exception ignore) {
                // resultJson 由本管理器 JsonTool.serialize 写入，格式异常理论不可达；回退为纯文本注入（无损）
            }
        }
        return msg.getContent();
    }

    // ==================== internals ====================

    private int nextSeq(String sessionId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("sessionId", sessionId));
        query.addOrderField("seq", true);
        List<NopDatavChatMessage> list = messageDao().findAllByQuery(query);
        return list.isEmpty() ? 1 : list.get(0).getSeq() + 1;
    }

    private NopDatavChatMessage newMessage(NopDatavChatSession session, int seq, String role, String content,
                                           String resultJson, String operator, Timestamp now) {
        NopDatavChatMessage msg = messageDao().newEntity();
        msg.setSessionId(session.getSessionId());
        msg.setSeq(seq);
        msg.setRole(role);
        msg.setContent(content != null ? content : "");
        msg.setResultJson(resultJson);
        msg.setDelFlag((byte) 0);
        msg.setVersion(0L);
        msg.setCreatedBy(operator);
        msg.setCreateTime(now);
        msg.setUpdatedBy(operator);
        msg.setUpdateTime(now);
        return msg;
    }

    private IEntityDao<NopDatavChatSession> sessionDao() {
        return daoProvider.daoFor(NopDatavChatSession.class);
    }

    private IEntityDao<NopDatavChatMessage> messageDao() {
        return daoProvider.daoFor(NopDatavChatMessage.class);
    }
}
