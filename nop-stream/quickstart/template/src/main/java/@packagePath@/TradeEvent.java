package @package@;

import java.io.Serializable;

/**
 * 贯穿三个入门拓扑的示例事件类型。
 *
 * <p>字段经 JavaBean getter 暴露，XDSL 内联 xpl 谓词以 {@code event.xxx} 属性访问语法读取
 * （见 topology2/topology3 的 {@code .stream.xml}）。
 */
public class TradeEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件时间（epoch millis），事件时间窗口与 CEP within 语义的时间轴。 */
    private final long eventTime;
    private final String userId;
    private final double amount;

    /** 由 topology2 的 keyed-state 富化算子写入的每用户序列号（1 起）。 */
    private transient int seq;

    public TradeEvent(long eventTime, String userId, double amount) {
        this.eventTime = eventTime;
        this.userId = userId;
        this.amount = amount;
    }

    public long getEventTime() {
        return eventTime;
    }

    public String getUserId() {
        return userId;
    }

    public double getAmount() {
        return amount;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    @Override
    public String toString() {
        return "TradeEvent(" + userId + ", t=" + eventTime + ", amount=" + amount + ", seq=" + seq + ")";
    }
}
