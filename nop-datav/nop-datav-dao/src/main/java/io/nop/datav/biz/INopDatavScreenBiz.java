
package io.nop.datav.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;

import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;

import java.util.List;

public interface INopDatavScreenBiz extends ICrudBiz<NopDatavScreen> {

    @BizMutation("publishScreen")
    NopDatavScreenSnapshot publishScreen(@Name("id") String id, IServiceContext context);

    @BizQuery("getPublishedScreen")
    NopDatavScreenSnapshot getPublishedScreen(@Name("id") String id, IServiceContext context);

    @BizMutation("rollbackScreen")
    NopDatavScreenSnapshot rollbackScreen(@Name("id") String id,
                                          @Name("snapshotVersion") long snapshotVersion,
                                          IServiceContext context);

    @BizQuery("getScreenLayout")
    ScreenLayoutConfig getScreenLayout(@Name("id") String id, IServiceContext context);

    @BizQuery("getComponentTypes")
    List<PanelComponentMeta> getComponentTypes(IServiceContext context);

    // ==================== D4-4 发布生命周期增强 ====================

    /**
     * 浏览指定大屏的全部发布历史（D4-4 §12.1）。
     *
     * <p>返回每版本的元信息（snapshotVersion/publishedBy/publishedTime，不含 snapshotContent），
     * 按版本号倒序。已发布内容对所有有读权限的用户可见（与 {@code getPublishedScreen} 同语义）。</p>
     */
    @BizQuery("getScreenSnapshotHistory")
    List<ScreenSnapshotHistory> getScreenSnapshotHistory(@Name("id") String id, IServiceContext context);

    /**
     * 查看指定历史版本的布局（D4-4 §12.2）。
     *
     * <p>读指定版本快照 → 经 {@code ScreenLayoutParser} 解析为 {@link ScreenLayoutConfig}。
     * 指定版本不存在抛 {@code ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND}。</p>
     */
    @BizQuery("getScreenLayoutByVersion")
    ScreenLayoutConfig getScreenLayoutByVersion(@Name("id") String id,
                                                 @Name("snapshotVersion") long snapshotVersion,
                                                 IServiceContext context);

    /**
     * 草稿预览（D4-4 §12.4）。
     *
     * <p>从当前编辑态（screen 主表 + widget 行集合）构建 {@link ScreenLayoutConfig}，
     * 无需先 publish。实现 = serializeScreenContent(当前编辑态) → ScreenLayoutParser.parse
     * （不经快照表落盘）。编辑态语义，仅 owner/admin 可预览（区别于已发布内容的 admin/user 可读）。</p>
     */
    @BizQuery("getScreenDraftLayout")
    ScreenLayoutConfig getScreenDraftLayout(@Name("id") String id, IServiceContext context);

    /**
     * 设置大屏缩略图（D4-4 §12.3）。
     *
     * <p>更新主表 thumbnail 列（**唯一**写入点；publish 不触碰 thumbnail）。
     * 编辑态语义，仅 owner/admin 可设置。{@code thumbnail} 参数为文件记录引用 ID 或 data URL。</p>
     */
    @BizMutation("setScreenThumbnail")
    NopDatavScreen setScreenThumbnail(@Name("id") String id, @Name("thumbnail") String thumbnail,
                                       IServiceContext context);
}
