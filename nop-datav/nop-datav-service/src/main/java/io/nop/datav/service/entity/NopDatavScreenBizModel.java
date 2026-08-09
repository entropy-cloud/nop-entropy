package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.datav.biz.INopDatavScreenBiz;
import io.nop.datav.biz.ScreenLayoutConfig;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;
import io.nop.datav.dao.entity.NopDatavScreenWidget;
import io.nop.datav.service.NopDatavOperatorResolver;
import io.nop.datav.service.component.PanelComponentRegistry;
import io.nop.datav.service.screen.ScreenLayoutParser;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND;

/**
 * 大屏 BizModel（D4-1）。
 *
 * <p>标准 CRUD（继承 CrudBizModel）+ publish/getPublished/rollback（复用 D0 主表+快照表模式，操作
 * {@link NopDatavScreenSnapshot}）+ {@code getScreenLayout}（按 Phase 1 契约读已发布快照并返回
 * 解析后的 {@link ScreenLayoutConfig} + 适配配置）。</p>
 *
 * <p>action 经 {@code requireEntity → checkDataAuth}；权限点 + 角色绑定定义于
 * {@code nop-datav-web/.../nop/datav/auth/nop-datav.action-auth.xml}，
 * RLS 定义于 {@code nop-datav-service/.../nop/datav/auth/nop-datav.data-auth.xml}。</p>
 *
 * <p>参见 {@code ai-dev/design/nop-datav/screen-design.md}。</p>
 */
@BizModel("NopDatavScreen")
public class NopDatavScreenBizModel extends CrudBizModel<NopDatavScreen>
        implements INopDatavScreenBiz {

    public static final int PUBLISH_STATUS_DRAFT = 0;
    public static final int PUBLISH_STATUS_PUBLISHED = 10;

    @jakarta.inject.Inject
    protected IJdbcTemplate jdbcTemplate;

    private final ScreenLayoutParser layoutParser;

    public NopDatavScreenBizModel() {
        setEntityName(NopDatavScreen.class.getName());
        // 直接引用 PanelComponentRegistry 单例（D1-1 注册表，代码构建自包含，不依赖 IoC 注入）
        this.layoutParser = new ScreenLayoutParser(PanelComponentRegistry.getInstance());
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavScreen:publishScreen")
    public NopDatavScreenSnapshot publishScreen(@Name("id") String id, IServiceContext context) {
        NopDatavScreen screen = requireEntity(id, "publishScreen", context);

        String snapshotContent = serializeScreenContent(screen);
        long nextVersion = calculateNextVersion(id);
        String publishedBy = NopDatavOperatorResolver.resolveOperator(context);
        Timestamp publishedTime = new Timestamp(System.currentTimeMillis());

        NopDatavScreenSnapshot snapshot = daoProvider()
                .daoFor(NopDatavScreenSnapshot.class).newEntity();
        snapshot.setSnapshotId(generateSnapshotId());
        snapshot.setScreenId(screen.getScreenId());
        snapshot.setSnapshotVersion(nextVersion);
        snapshot.setSnapshotContent(snapshotContent);
        snapshot.setPublishedBy(publishedBy);
        snapshot.setPublishedTime(publishedTime);
        snapshot.setVersion(0L);
        snapshot.setCreatedBy(publishedBy);
        snapshot.setCreateTime(publishedTime);
        snapshot.setUpdatedBy(publishedBy);
        snapshot.setUpdateTime(publishedTime);

        daoProvider().daoFor(NopDatavScreenSnapshot.class).saveEntityDirectly(snapshot);

        updateScreenPublishState(screen.getScreenId(), PUBLISH_STATUS_PUBLISHED,
                nextVersion, publishedBy, publishedTime);

        afterEntityChange(screen, "publishScreen", context);
        return snapshot;
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavScreen:getPublishedScreen")
    public NopDatavScreenSnapshot getPublishedScreen(@Name("id") String id, IServiceContext context) {
        NopDatavScreen screen = requireEntity(id, "getPublishedScreen", context);

        NopDatavScreenSnapshot snapshot = findLatestSnapshot(screen.getScreenId());
        if (snapshot == null) {
            throw new NopException(ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND)
                    .param("screenId", screen.getScreenId());
        }
        return snapshot;
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavScreen:rollbackScreen")
    public NopDatavScreenSnapshot rollbackScreen(@Name("id") String id,
                                                  @Name("snapshotVersion") long snapshotVersion,
                                                  IServiceContext context) {
        NopDatavScreen screen = requireEntity(id, "rollbackScreen", context);

        NopDatavScreenSnapshot snapshot = findSnapshotByVersion(screen.getScreenId(), snapshotVersion);
        if (snapshot == null) {
            throw new NopException(ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND)
                    .param("screenId", screen.getScreenId())
                    .param("snapshotVersion", snapshotVersion);
        }

        restoreScreenFromSnapshot(screen, snapshot);
        updateScreenFields(screen.getScreenId(), screen);

        afterEntityChange(screen, "rollbackScreen", context);
        return snapshot;
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavScreen:getScreenLayout")
    public ScreenLayoutConfig getScreenLayout(@Name("id") String id, IServiceContext context) {
        NopDatavScreen screen = requireEntity(id, "getScreenLayout", context);

        NopDatavScreenSnapshot snapshot = findLatestSnapshot(screen.getScreenId());
        if (snapshot == null) {
            throw new NopException(ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND)
                    .param("screenId", screen.getScreenId());
        }
        // 解析时执行完整运行时校验：JSON 非法 / widget 越界 / 未知组件类型（经 PanelComponentRegistry）
        return layoutParser.parse(screen.getScreenId(), snapshot);
    }

    private String serializeScreenContent(NopDatavScreen screen) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("screenName", screen.getScreenName());
        content.put("displayName", screen.getDisplayName());
        content.put("description", screen.getDescription());
        content.put("screenWidth", screen.getScreenWidth());
        content.put("screenHeight", screen.getScreenHeight());
        content.put("adaptorMode", screen.getAdaptorMode() == null
                ? io.nop.datav.service.screen.ScreenAdaptorMode.FULL
                : screen.getAdaptorMode());
        content.put("backgroundConfig", parseJson(screen.getBackgroundConfig()));
        content.put("widgets", serializeWidgets(screen.getScreenId()));
        return JsonTool.stringify(content);
    }

    private List<Map<String, Object>> serializeWidgets(String screenId) {
        List<NopDatavScreenWidget> widgets = findRelatedEntities(NopDatavScreenWidget.class,
                "screenId", screenId, "z");
        List<Map<String, Object>> result = new ArrayList<>(widgets.size());
        for (NopDatavScreenWidget widget : widgets) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("widgetId", widget.getWidgetId());
            map.put("widgetName", widget.getWidgetName());
            map.put("displayName", widget.getDisplayName());
            map.put("componentType", widget.getComponentType());
            map.put("datasetRefId", widget.getDatasetRefId());
            map.put("x", widget.getX());
            map.put("y", widget.getY());
            map.put("w", widget.getW());
            map.put("h", widget.getH());
            map.put("z", widget.getZ());
            map.put("widgetConfig", parseJson(widget.getWidgetConfig()));
            result.add(map);
        }
        return result;
    }

    private <T extends IDaoEntity> List<T> findRelatedEntities(Class<T> entityClass, String filterField,
                                                               String filterValue, String orderField) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(filterField, filterValue));
        if (orderField != null) {
            query.addOrderField(orderField, false);
        }
        @SuppressWarnings("unchecked")
        List<T> list = (List<T>) daoProvider().daoFor(entityClass).findAllByQuery(query);
        return list;
    }

    private long calculateNextVersion(String screenId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("screenId", screenId));
        query.addOrderField("snapshotVersion", true);
        query.setLimit(1);
        NopDatavScreenSnapshot latest = daoProvider()
                .daoFor(NopDatavScreenSnapshot.class).findFirstByQuery(query);
        return latest == null ? 1L : latest.getSnapshotVersion() + 1;
    }

    private NopDatavScreenSnapshot findLatestSnapshot(String screenId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("screenId", screenId));
        query.addOrderField("snapshotVersion", true);
        query.setLimit(1);
        return daoProvider().daoFor(NopDatavScreenSnapshot.class).findFirstByQuery(query);
    }

    private NopDatavScreenSnapshot findSnapshotByVersion(String screenId, long snapshotVersion) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("screenId", screenId));
        query.addFilter(FilterBeans.eq("snapshotVersion", snapshotVersion));
        query.setLimit(1);
        return daoProvider().daoFor(NopDatavScreenSnapshot.class).findFirstByQuery(query);
    }

    @SuppressWarnings("unchecked")
    private void restoreScreenFromSnapshot(NopDatavScreen screen, NopDatavScreenSnapshot snapshot) {
        Map<String, Object> content = JsonTool.parseMap(snapshot.getSnapshotContent());
        if (content == null) {
            throw new NopException(ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND)
                    .param("screenId", screen.getScreenId());
        }

        Object backgroundConfig = content.get("backgroundConfig");
        screen.setBackgroundConfig(backgroundConfig == null ? null : JsonTool.stringify(backgroundConfig));

        Object screenWidth = content.get("screenWidth");
        if (screenWidth instanceof Number) {
            screen.setScreenWidth(((Number) screenWidth).intValue());
        }
        Object screenHeight = content.get("screenHeight");
        if (screenHeight instanceof Number) {
            screen.setScreenHeight(((Number) screenHeight).intValue());
        }
        Object adaptorMode = content.get("adaptorMode");
        if (adaptorMode instanceof Number) {
            screen.setAdaptorMode(((Number) adaptorMode).intValue());
        }

        screen.setPublishStatus(PUBLISH_STATUS_PUBLISHED);
        screen.setPublishedVersion(snapshot.getSnapshotVersion());
        screen.setPublishedBy(snapshot.getPublishedBy());
        screen.setPublishedTime(snapshot.getPublishedTime());
    }

    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JsonTool.parse(json);
    }

    private String generateSnapshotId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private void updateScreenPublishState(String screenId, int publishStatus, long publishedVersion,
                                           String publishedBy, Timestamp publishedTime) {
        jdbcTemplate.executeUpdate(SQL.begin().name("updateScreenPublishState")
                .sql("update NOP_DATAV_SCREEN set PUBLISH_STATUS=").param(publishStatus)
                .sql(",PUBLISHED_VERSION=").param(publishedVersion)
                .sql(",PUBLISHED_BY=").param(publishedBy)
                .sql(",PUBLISHED_TIME=").param(publishedTime)
                .sql(" where SCREEN_ID=").param(screenId).end());
    }

    private void updateScreenFields(String screenId, NopDatavScreen source) {
        jdbcTemplate.executeUpdate(SQL.begin().name("updateScreenFields")
                .sql("update NOP_DATAV_SCREEN set BACKGROUND_CONFIG=").param(source.getBackgroundConfig())
                .sql(",SCREEN_WIDTH=").param(source.getScreenWidth())
                .sql(",SCREEN_HEIGHT=").param(source.getScreenHeight())
                .sql(",ADAPTOR_MODE=").param(source.getAdaptorMode())
                .sql(",PUBLISH_STATUS=").param(source.getPublishStatus())
                .sql(",PUBLISHED_VERSION=").param(source.getPublishedVersion())
                .sql(",PUBLISHED_BY=").param(source.getPublishedBy())
                .sql(",PUBLISHED_TIME=").param(source.getPublishedTime())
                .sql(" where SCREEN_ID=").param(screenId).end());
    }

    /**
     * 内部辅助：暴露 layoutParser 用于单元测试直接验证解析逻辑（不经 IoC）。
     */
    static ScreenLayoutParser getLayoutParserForTest() {
        return new ScreenLayoutParser(PanelComponentRegistry.getInstance());
    }

    /**
     * 内部辅助：暴露错误码常量给测试断言引用（避免魔法值）。
     */
    static NopException screenNotFound(String screenId) {
        return new NopException(ERR_DATAV_SCREEN_NOT_FOUND).param("screenId", screenId);
    }
}
