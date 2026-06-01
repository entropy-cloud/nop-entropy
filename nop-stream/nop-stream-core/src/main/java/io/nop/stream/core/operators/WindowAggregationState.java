package io.nop.stream.core.operators;

import java.io.Serializable;
import java.util.Map;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
/**
 * @deprecated Use {@link io.nop.stream.core.operators.IWindowOperatorFactory} with
 * {@code WindowOperator} from the runtime module instead.
 */
@Deprecated
public class WindowAggregationState implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int CURRENT_VERSION = 1;

    private int version = CURRENT_VERSION;
    private String keyClassName;
    private String windowClassName;
    private Map<String, Object> windowState;
    private Map<String, Object> eventTimeTimers;
    private Map<String, Object> processingTimeTimers;
    private Map<String, Object> triggerState;
    private long currentWatermark;

    public WindowAggregationState() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getKeyClassName() {
        return keyClassName;
    }

    public void setKeyClassName(String keyClassName) {
        this.keyClassName = keyClassName;
    }

    public String getWindowClassName() {
        return windowClassName;
    }

    public void setWindowClassName(String windowClassName) {
        this.windowClassName = windowClassName;
    }

    public Map<String, Object> getWindowState() {
        return windowState;
    }

    public void setWindowState(Map<String, Object> windowState) {
        this.windowState = windowState;
    }

    public Map<String, Object> getEventTimeTimers() {
        return eventTimeTimers;
    }

    public void setEventTimeTimers(Map<String, Object> eventTimeTimers) {
        this.eventTimeTimers = eventTimeTimers;
    }

    public Map<String, Object> getProcessingTimeTimers() {
        return processingTimeTimers;
    }

    public void setProcessingTimeTimers(Map<String, Object> processingTimeTimers) {
        this.processingTimeTimers = processingTimeTimers;
    }

    public Map<String, Object> getTriggerState() {
        return triggerState;
    }

    public void setTriggerState(Map<String, Object> triggerState) {
        this.triggerState = triggerState;
    }

    public long getCurrentWatermark() {
        return currentWatermark;
    }

    public void setCurrentWatermark(long currentWatermark) {
        this.currentWatermark = currentWatermark;
    }
}
