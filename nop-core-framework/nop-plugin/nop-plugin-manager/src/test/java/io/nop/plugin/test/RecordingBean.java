package io.nop.plugin.test;

/**
 * 带 destroy-method 的记录 bean——deactivate 时子容器 stop 触发 bean destroy
 * （destroy-method="onDestroy"），事件顺序 = effect 回退在前、bean destroy 在后。
 */
public class RecordingBean {

    public void onDestroy() {
        AgentInstanceRecorder.event("bean-destroyed");
    }
}
