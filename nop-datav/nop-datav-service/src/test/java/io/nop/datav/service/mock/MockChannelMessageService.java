package io.nop.datav.service.mock;

import io.nop.integration.api.channel.IChannelMessageService;
import io.nop.integration.api.channel.IInboundMessageListener;
import io.nop.integration.api.channel.OutboundChannelMessage;
import io.nop.integration.api.channel.SendResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 测试用 mock {@link IChannelMessageService}（IM 渠道通知接入）。
 *
 * <p>记录所有 {@code sendToUser} 调用（userId + message）供测试断言
 * （{@link #getCalls()}、{@link #getSendCount()}），不实际发送。可编程控制返回结果：
 * <ul>
 *   <li>{@link #setResultToSend(SendResult)}——全局默认返回（默认 {@link SendResult#SENT}）；</li>
 *   <li>{@link #setNoBindingUsers(Set)}——指定 userId 返回 {@link SendResult#NO_BINDING}；</li>
 *   <li>{@link #setFailOnSend(RuntimeException)}——非 null 时抛异常（模拟连接器/网络错误）。</li>
 * </ul>
 * 通过 {@code test-report-mock.beans.xml} 注册为 {@code primary=true} 覆盖生产实现。</p>
 */
public class MockChannelMessageService implements IChannelMessageService {

    private final List<CallRecord> calls = Collections.synchronizedList(new ArrayList<>());

    private SendResult resultToSend = SendResult.SENT;
    private Set<String> noBindingUsers;
    private RuntimeException failOnSend;

    @Override
    public SendResult sendToUser(String userId, OutboundChannelMessage message) {
        calls.add(new CallRecord(userId, message));
        if (failOnSend != null) {
            throw failOnSend;
        }
        if (noBindingUsers != null && noBindingUsers.contains(userId)) {
            return SendResult.NO_BINDING;
        }
        return resultToSend;
    }

    @Override
    public void subscribeInbound(IInboundMessageListener listener) {
        // no-op：IM 入站不在测试范围（仅出站主动通知）
    }

    public List<CallRecord> getCalls() {
        synchronized (calls) {
            return new ArrayList<>(calls);
        }
    }

    public int getSendCount() {
        return calls.size();
    }

    public void setResultToSend(SendResult resultToSend) {
        this.resultToSend = resultToSend;
    }

    public void setNoBindingUsers(Set<String> noBindingUsers) {
        this.noBindingUsers = noBindingUsers == null ? null : new HashSet<>(noBindingUsers);
    }

    public void setFailOnSend(RuntimeException failOnSend) {
        this.failOnSend = failOnSend;
    }

    public void reset() {
        calls.clear();
        resultToSend = SendResult.SENT;
        noBindingUsers = null;
        failOnSend = null;
    }

    /** 单次 sendToUser 调用记录（userId + 出站消息快照）。 */
    public static final class CallRecord {
        public final String userId;
        public final OutboundChannelMessage message;

        public CallRecord(String userId, OutboundChannelMessage message) {
            this.userId = userId;
            this.message = message;
        }
    }
}
