package io.nop.datav.biz;

import java.sql.Timestamp;

/**
 * 大屏发布历史的单条元信息（D4-4 §12.1）。
 *
 * <p>{@code getScreenSnapshotHistory} 返回此类的列表，每条对应一次 publish（一个快照版本）。
 * 仅含版本元信息（snapshotVersion/publishedBy/publishedTime），<b>不含</b> snapshotContent
 * （CLOB 大、列表场景浪费；具体内容按需经 {@code getScreenLayoutByVersion} 取单条）。</p>
 *
 * <p>位于 nop-datav-dao 的 biz 包，仅使用基础 Java 类型，不引用服务层类型，
 * 保持依赖方向正确（{@link INopDatavScreenBiz} 接口位于 dao 模块）。</p>
 */
public class ScreenSnapshotHistory {

    private long snapshotVersion;
    private String publishedBy;
    private Timestamp publishedTime;

    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(long snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(String publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Timestamp getPublishedTime() {
        return publishedTime;
    }

    public void setPublishedTime(Timestamp publishedTime) {
        this.publishedTime = publishedTime;
    }
}
