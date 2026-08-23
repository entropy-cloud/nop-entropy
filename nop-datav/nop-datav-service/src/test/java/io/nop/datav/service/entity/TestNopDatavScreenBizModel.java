package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavScreenBiz;
import io.nop.datav.biz.PanelComponentMeta;
import io.nop.datav.biz.ScreenLayoutConfig;
import io.nop.datav.biz.ScreenSnapshotHistory;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;
import io.nop.datav.dao.entity.NopDatavScreenWidget;
import io.nop.datav.service.component.PanelComponentTypes;
import io.nop.datav.service.screen.ScreenAdaptorMode;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.impl.OrmTemplateImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_SCREEN_LAYOUT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_UNKNOWN_COMPONENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端集成测试 {@link NopDatavScreenBizModel}。
 *
 * <p>覆盖：创建大屏 → 配置画布/widget → 发布 → 读取已发布 → 适配解析（getScreenLayout）
 * → 断言 widget 定位/组件类型/适配模式正确；以及发布/快照/回滚语义。</p>
 *
 * <p>包含 Anti-Hollow 接线验证：{@code getScreenLayout} 经 {@link io.nop.datav.service.component.PanelComponentRegistry#requireComponent}
 * 实际调用——通过断言未知组件类型抛 {@code ERR_DATAV_UNKNOWN_COMPONENT_TYPE} 证明运行时连通（非空方法体）。</p>
 */
public class TestNopDatavScreenBizModel extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavScreenBiz screenBiz;

    // ==================== CRUD ====================

    @Test
    public void testScreenCrud() {
        NopDatavScreen screen = newScreen("screen-crud", "crud-screen", 1920, 1080,
                ScreenAdaptorMode.FULL);
        daoProvider.daoFor(NopDatavScreen.class).saveEntityDirectly(screen);

        NopDatavScreen loaded = daoProvider.daoFor(NopDatavScreen.class).getEntityById("screen-crud");
        assertNotNull(loaded);
        assertEquals("crud-screen", loaded.getScreenName());
        assertEquals(1920, loaded.getScreenWidth());
        assertEquals(1080, loaded.getScreenHeight());
        assertEquals(ScreenAdaptorMode.FULL, loaded.getAdaptorMode());
    }

    @Test
    public void testWidgetCrud() {
        NopDatavScreen screen = saveScreen("screen-widget-crud", "widget-crud");
        NopDatavScreenWidget widget = newWidget("widget-1", screen.getScreenId(),
                "chart", 10, 20, 300, 200, 0);
        daoProvider.daoFor(NopDatavScreenWidget.class).saveEntityDirectly(widget);

        NopDatavScreenWidget loaded = daoProvider.daoFor(NopDatavScreenWidget.class)
                .getEntityById("widget-1");
        assertNotNull(loaded);
        assertEquals(screen.getScreenId(), loaded.getScreenId());
        assertEquals("chart", loaded.getComponentType());
        assertEquals(10, loaded.getX());
        assertEquals(20, loaded.getY());
        assertEquals(300, loaded.getW());
        assertEquals(200, loaded.getH());
    }

    // ==================== Publish / Snapshot ====================

    @Test
    public void testPublishScreenCreatesSnapshotAndUpdatesMainTable() {
        NopDatavScreen screen = saveScreen("screen-pub", "publish-screen");
        screen.setBackgroundConfig(JsonTool.stringify(Map.of("color", "#222")));
        daoProvider.daoFor(NopDatavScreen.class).updateEntityDirectly(screen);
        saveWidget("widget-pub-1", screen.getScreenId(), "chart", 0, 0, 600, 400, 0);

        IServiceContext context = newContext("alice");

        NopDatavScreenSnapshot snapshot = screenBiz.publishScreen(screen.getScreenId(), context);

        assertNotNull(snapshot);
        assertEquals(1L, snapshot.getSnapshotVersion());
        assertEquals("alice", snapshot.getPublishedBy());
        assertNotNull(snapshot.getSnapshotContent());
        assertNotNull(snapshot.getPublishedTime());

        Map<String, Object> content = JsonTool.parseMap(snapshot.getSnapshotContent());
        assertNotNull(content);
        assertEquals(1920, ((Number) content.get("screenWidth")).intValue());
        assertEquals(1080, ((Number) content.get("screenHeight")).intValue());
        assertEquals(ScreenAdaptorMode.FULL, ((Number) content.get("adaptorMode")).intValue());
        assertEquals(1, ((java.util.List<?>) content.get("widgets")).size());

        NopDatavScreen updated = daoProvider.daoFor(NopDatavScreen.class)
                .getEntityById(screen.getScreenId());
        assertEquals(NopDatavScreenBizModel.PUBLISH_STATUS_PUBLISHED, updated.getPublishStatus());
        assertEquals(1L, updated.getPublishedVersion());
        assertEquals("alice", updated.getPublishedBy());
    }

    @Test
    public void testPublishTwiceIncrementsVersion() {
        NopDatavScreen screen = saveScreen("screen-pub-2", "publish-twice");
        IServiceContext context = newContext("bob");

        NopDatavScreenSnapshot snap1 = screenBiz.publishScreen(screen.getScreenId(), context);
        assertEquals(1L, snap1.getSnapshotVersion());

        NopDatavScreenSnapshot snap2 = screenBiz.publishScreen(screen.getScreenId(), context);
        assertEquals(2L, snap2.getSnapshotVersion());
        assertNotEquals(snap1.getSnapshotId(), snap2.getSnapshotId());

        NopDatavScreen updated = daoProvider.daoFor(NopDatavScreen.class)
                .getEntityById(screen.getScreenId());
        assertEquals(2L, updated.getPublishedVersion());
    }

    @Test
    public void testGetPublishedScreenReturnsLatestSnapshot() {
        NopDatavScreen screen = saveScreen("screen-getpub", "get-published");
        IServiceContext context = newContext("alice");

        screenBiz.publishScreen(screen.getScreenId(), context);
        NopDatavScreenSnapshot snap2 = screenBiz.publishScreen(screen.getScreenId(), context);

        NopDatavScreenSnapshot published = screenBiz.getPublishedScreen(screen.getScreenId(), context);

        assertNotNull(published);
        assertEquals(snap2.getSnapshotVersion(), published.getSnapshotVersion());
    }

    @Test
    public void testGetPublishedScreenThrowsWhenNotPublished() {
        NopDatavScreen screen = saveScreen("screen-nopub", "no-publish");
        IServiceContext context = newContext("alice");

        NopException ex = assertThrows(NopException.class,
                () -> screenBiz.getPublishedScreen(screen.getScreenId(), context));
        assertEquals(ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testRollbackScreenRestoresFromHistoricalSnapshot() {
        NopDatavScreen screen = saveScreen("screen-rollback", "rollback-screen");
        screen.setAdaptorMode(ScreenAdaptorMode.HEIGHT_FIRST);
        daoProvider.daoFor(NopDatavScreen.class).updateEntityDirectly(screen);

        IServiceContext context = newContext("alice");

        NopDatavScreenSnapshot snap1 = screenBiz.publishScreen(screen.getScreenId(), context);

        // change adaptorMode and publish v2（publish 现为实体写会 bump version——plan 2255，
        // 跨会话脱管实体需重读获取新 version 后再改，否则乐观锁 update-entity-not-found）
        screen = daoProvider.daoFor(NopDatavScreen.class).getEntityById(screen.getScreenId());
        screen.setAdaptorMode(ScreenAdaptorMode.KEEP);
        daoProvider.daoFor(NopDatavScreen.class).updateEntityDirectly(screen);
        screenBiz.publishScreen(screen.getScreenId(), context);

        // rollback to v1 (HEIGHT_FIRST)
        NopDatavScreenSnapshot rolled = screenBiz.rollbackScreen(
                screen.getScreenId(), snap1.getSnapshotVersion(), context);

        assertNotNull(rolled);
        assertEquals(snap1.getSnapshotVersion(), rolled.getSnapshotVersion());

        NopDatavScreen restored = daoProvider.daoFor(NopDatavScreen.class)
                .getEntityById(screen.getScreenId());
        assertEquals(ScreenAdaptorMode.HEIGHT_FIRST, restored.getAdaptorMode());
        assertEquals(snap1.getSnapshotVersion(), restored.getPublishedVersion());
    }

    @Test
    public void testRollbackThrowsForNonExistentVersion() {
        NopDatavScreen screen = saveScreen("screen-rollback-404", "rollback-404");
        IServiceContext context = newContext("alice");

        screenBiz.publishScreen(screen.getScreenId(), context);

        NopException ex = assertThrows(NopException.class,
                () -> screenBiz.rollbackScreen(screen.getScreenId(), 999L, context));
        assertEquals(ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    // ==================== getScreenLayout (E2E + Anti-Hollow) ====================

    @Test
    public void testGetScreenLayoutEndToEnd() {
        IServiceContext context = newContext("e2e-user");

        // 1. Create screen with canvas size + adaptor
        NopDatavScreen screen = saveScreen("screen-e2e", "e2e-screen");
        screen.setAdaptorMode(ScreenAdaptorMode.HEIGHT_FIRST);
        screen.setBackgroundConfig(JsonTool.stringify(Map.of("color", "#123456")));
        daoProvider.daoFor(NopDatavScreen.class).updateEntityDirectly(screen);

        // 2. Add a chart widget with absolute positioning
        saveWidget("widget-e2e-1", screen.getScreenId(), "chart", 100, 200, 600, 400, 5);
        saveWidget("widget-e2e-2", screen.getScreenId(), "text", 700, 50, 200, 100, 1);

        // 3. Publish
        screenBiz.publishScreen(screen.getScreenId(), context);

        // 4. getScreenLayout returns parsed layout + adaptation
        ScreenLayoutConfig layout = screenBiz.getScreenLayout(screen.getScreenId(), context);

        assertNotNull(layout);
        assertEquals("screen-e2e", layout.getScreenId());
        assertEquals(1L, layout.getSnapshotVersion());

        // Canvas assertions
        assertNotNull(layout.getCanvas());
        assertEquals(1920, layout.getCanvas().getWidth());
        assertEquals(1080, layout.getCanvas().getHeight());
        assertEquals(ScreenAdaptorMode.HEIGHT_FIRST, layout.getCanvas().getAdaptorMode());
        assertNotNull(layout.getCanvas().getBackgroundConfig());
        assertEquals("#123456", layout.getCanvas().getBackgroundConfig().get("color"));

        // Adaptation mirrors canvas
        assertEquals(1920, layout.getAdaptation().getBaseWidth());
        assertEquals(1080, layout.getAdaptation().getBaseHeight());
        assertEquals(ScreenAdaptorMode.HEIGHT_FIRST, layout.getAdaptation().getAdaptorMode());

        // Widgets assertions
        assertEquals(2, layout.getWidgets().size());
        ScreenLayoutConfig.Widget w1 = layout.getWidgets().stream()
                .filter(w -> "widget-e2e-1".equals(w.getWidgetId())).findFirst().orElseThrow();
        assertEquals("chart", w1.getComponentType());
        assertEquals(100, w1.getX());
        assertEquals(200, w1.getY());
        assertEquals(600, w1.getW());
        assertEquals(400, w1.getH());
        assertEquals(5, w1.getZ());
    }

    /**
     * Anti-Hollow 接线验证：getScreenLayout 经 PanelComponentRegistry.requireComponent 实际调用。
     * 若未调用（空方法体/静默跳过），未知组件类型不会抛 ERR_DATAV_UNKNOWN_COMPONENT_TYPE。
     */
    @Test
    public void testGetScreenLayoutRejectsUnknownComponentType() {
        IServiceContext context = newContext("e2e-user");

        NopDatavScreen screen = saveScreen("screen-unknown-comp", "unknown-comp-screen");

        // Manually craft a snapshot with unknown component type to bypass widget entity save
        saveWidget("widget-unknown", screen.getScreenId(), "chart", 0, 0, 100, 100, 0);
        screenBiz.publishScreen(screen.getScreenId(), context);

        // Tamper with snapshot content to inject unknown component type
        NopDatavScreenSnapshot snapshot = screenBiz.getPublishedScreen(screen.getScreenId(), context);
        Map<String, Object> content = JsonTool.parseMap(snapshot.getSnapshotContent());
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> widgets = (java.util.List<Map<String, Object>>) content.get("widgets");
        widgets.get(0).put("componentType", "non-existent-component");
        snapshot.setSnapshotContent(JsonTool.stringify(content));
        daoProvider.daoFor(NopDatavScreenSnapshot.class).updateEntityDirectly(snapshot);

        // getScreenLayout must call requireComponent → throw on unknown type
        NopException ex = assertThrows(NopException.class,
                () -> screenBiz.getScreenLayout(screen.getScreenId(), context));
        assertEquals(ERR_DATAV_UNKNOWN_COMPONENT_TYPE.getErrorCode(), ex.getErrorCode(),
                "getScreenLayout must invoke PanelComponentRegistry.requireComponent (anti-hollow)");
    }

    @Test
    public void testGetScreenLayoutRejectsOutOfBoundsWidget() {
        IServiceContext context = newContext("e2e-user");

        NopDatavScreen screen = saveScreen("screen-oob", "oob-screen");
        saveWidget("widget-oob", screen.getScreenId(), "chart", 0, 0, 100, 100, 0);
        screenBiz.publishScreen(screen.getScreenId(), context);

        // Tamper: make widget exceed canvas
        NopDatavScreenSnapshot snapshot = screenBiz.getPublishedScreen(screen.getScreenId(), context);
        Map<String, Object> content = JsonTool.parseMap(snapshot.getSnapshotContent());
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> widgets = (java.util.List<Map<String, Object>>) content.get("widgets");
        // canvas 1920x1080; set widget w=2000 → x+w=2000 > 1920
        widgets.get(0).put("w", 2000);
        snapshot.setSnapshotContent(JsonTool.stringify(content));
        daoProvider.daoFor(NopDatavScreenSnapshot.class).updateEntityDirectly(snapshot);

        NopException ex = assertThrows(NopException.class,
                () -> screenBiz.getScreenLayout(screen.getScreenId(), context));
        assertEquals(ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testGetScreenLayoutRejectsInvalidJson() {
        IServiceContext context = newContext("e2e-user");

        NopDatavScreen screen = saveScreen("screen-bad-json", "bad-json-screen");
        screenBiz.publishScreen(screen.getScreenId(), context);

        NopDatavScreenSnapshot snapshot = screenBiz.getPublishedScreen(screen.getScreenId(), context);
        snapshot.setSnapshotContent("{ invalid json");
        daoProvider.daoFor(NopDatavScreenSnapshot.class).updateEntityDirectly(snapshot);

        NopException ex = assertThrows(NopException.class,
                () -> screenBiz.getScreenLayout(screen.getScreenId(), context));
        assertEquals(ERR_DATAV_INVALID_SCREEN_LAYOUT.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testGetScreenLayoutThrowsWhenNotPublished() {
        NopDatavScreen screen = saveScreen("screen-layout-nopub", "layout-no-pub");
        IServiceContext context = newContext("alice");

        NopException ex = assertThrows(NopException.class,
                () -> screenBiz.getScreenLayout(screen.getScreenId(), context));
        assertEquals(ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    // ==================== D4-3 主题：色板 + 背景 E2E ====================

    /**
     * 端到端验证（rule #22）：创建大屏（backgroundConfig 配置 {palette, background}）→
     * 添加 widget（widgetConfig.theme 含命名引用）→ publish → getScreenLayout 返回：
     * <ul>
     *   <li>theme 字段（结构化 palette/background，缺省值填充）</li>
     *   <li>widget resolvedTheme 命名引用已解析为实际色值</li>
     *   <li>Canvas.backgroundConfig 原样透传（非破坏）</li>
     * </ul>
     */
    @Test
    public void testThemePaletteAndBackgroundEndToEnd() {
        IServiceContext context = newContext("e2e-user");

        // 1. 创建大屏，backgroundConfig 配置结构化主题（palette 部分覆盖 + background image）
        Map<String, Object> palette = new java.util.LinkedHashMap<>();
        palette.put("primary", "#FF0000");
        Map<String, Object> background = Map.of("type", "image", "value", "https://example.com/bg.png");
        Map<String, Object> backgroundConfig = new java.util.LinkedHashMap<>();
        backgroundConfig.put("palette", palette);
        backgroundConfig.put("background", background);

        NopDatavScreen screen = saveScreen("screen-theme", "theme-screen");
        screen.setBackgroundConfig(JsonTool.stringify(backgroundConfig));
        daoProvider.daoFor(NopDatavScreen.class).updateEntityDirectly(screen);

        // 2. 添加 widget，widgetConfig.theme 含命名引用 + styleOptions 不被自动改写
        Map<String, Object> widgetTheme = new java.util.LinkedHashMap<>();
        widgetTheme.put("color", "primary");
        widgetTheme.put("backgroundColor", "background");
        Map<String, Object> widgetConfig = new java.util.LinkedHashMap<>();
        widgetConfig.put("theme", widgetTheme);
        widgetConfig.put("styleOptions", Map.of("color", "primary"));

        NopDatavScreenWidget widget = newWidget("widget-theme", screen.getScreenId(),
                "chart", 0, 0, 100, 100, 0);
        widget.setWidgetConfig(JsonTool.stringify(widgetConfig));
        daoProvider.daoFor(NopDatavScreenWidget.class).saveEntityDirectly(widget);

        // 3. Publish
        screenBiz.publishScreen(screen.getScreenId(), context);

        // 4. getScreenLayout 返回结构化主题
        ScreenLayoutConfig layout = screenBiz.getScreenLayout(screen.getScreenId(), context);

        assertNotNull(layout.getTheme());
        // palette：用户指定的覆盖
        assertEquals("#FF0000", layout.getTheme().getPalette().get("primary"));
        // 未指定的回退缺省
        assertEquals("#13C2C2", layout.getTheme().getPalette().get("secondary"));
        assertEquals("#52C41A", layout.getTheme().getPalette().get("success"));
        // background 解析
        assertEquals("image", layout.getTheme().getBackground().getType());
        assertEquals("https://example.com/bg.png", layout.getTheme().getBackground().getValue());

        // 5. widget resolvedTheme 命名引用已解析
        assertEquals(1, layout.getWidgets().size());
        ScreenLayoutConfig.Widget w = layout.getWidgets().get(0);
        assertNotNull(w.getResolvedTheme());
        assertEquals("#FF0000", w.getResolvedTheme().get("color"));
        // "background" 命名色 = palette.background 缺省值 #131A2E（用户未覆盖）
        assertEquals("#131A2E", w.getResolvedTheme().get("backgroundColor"));

        // 6. Canvas.backgroundConfig 原样透传（非破坏）
        assertNotNull(layout.getCanvas().getBackgroundConfig());
        @SuppressWarnings("unchecked")
        Map<String, Object> passedPalette = (Map<String, Object>) layout.getCanvas().getBackgroundConfig().get("palette");
        assertEquals("#FF0000", passedPalette.get("primary"));

        // 7. widgetConfig 原样透传（theme 区仍是命名引用 "primary"，styleOptions 不被改写）
        @SuppressWarnings("unchecked")
        Map<String, Object> passedWidgetTheme = (Map<String, Object>) w.getWidgetConfig().get("theme");
        assertEquals("primary", passedWidgetTheme.get("color"));
        assertEquals("primary", ((Map<?, ?>) w.getWidgetConfig().get("styleOptions")).get("color"));
    }

    /**
     * 向后兼容 E2E：legacy 自由格式 backgroundConfig（{"color":"#123456"}）经 publish → getScreenLayout：
     * theme 用缺省、Canvas.backgroundConfig 原样透传、不报错。既有 testGetScreenLayoutEndToEnd 行为不回归。
     */
    @Test
    public void testLegacyFreeformBackgroundConfigEndToEndUsesDefaultTheme() {
        IServiceContext context = newContext("e2e-user");

        NopDatavScreen screen = saveScreen("screen-legacy-theme", "legacy-theme-screen");
        screen.setBackgroundConfig(JsonTool.stringify(Map.of("color", "#123456")));
        daoProvider.daoFor(NopDatavScreen.class).updateEntityDirectly(screen);
        saveWidget("widget-legacy", screen.getScreenId(), "chart", 0, 0, 100, 100, 0);

        screenBiz.publishScreen(screen.getScreenId(), context);

        ScreenLayoutConfig layout = screenBiz.getScreenLayout(screen.getScreenId(), context);

        // theme 用缺省
        assertNotNull(layout.getTheme());
        assertEquals("#1890FF", layout.getTheme().getPalette().get("primary"));
        assertEquals("color", layout.getTheme().getBackground().getType());
        assertEquals("#131A2E", layout.getTheme().getBackground().getValue());

        // Canvas.backgroundConfig 原样透传
        assertEquals("#123456", layout.getCanvas().getBackgroundConfig().get("color"));
    }

    // ==================== D4-2 装饰/媒体组件 E2E + getComponentTypes API ====================

    /**
     * 端到端验证（rule #22）：创建大屏 → 添加 6 类装饰/媒体 widget → publish →
     * getScreenLayout 解析通过并断言 widget componentType 正确（证明装饰/媒体类型经注册表接线，非空壳）。
     */
    @Test
    public void testDecorativeMediaWidgetsPassGetScreenLayoutEndToEnd() {
        IServiceContext context = newContext("e2e-user");

        NopDatavScreen screen = saveScreen("screen-deco", "decorative-screen");
        // 6 类装饰/媒体组件，各放一个 widget
        saveWidget("w-border", screen.getScreenId(), PanelComponentTypes.DECORATIVE_BORDER, 0, 0, 200, 50, 0);
        saveWidget("w-scroll", screen.getScreenId(), PanelComponentTypes.SCROLL_TEXT, 0, 60, 1920, 30, 1);
        saveWidget("w-clock", screen.getScreenId(), PanelComponentTypes.TIME_CLOCK, 1700, 0, 220, 50, 2);
        saveWidget("w-video", screen.getScreenId(), PanelComponentTypes.VIDEO, 100, 200, 600, 400, 0);
        saveWidget("w-stream", screen.getScreenId(), PanelComponentTypes.STREAM, 800, 200, 600, 400, 0);
        saveWidget("w-carousel", screen.getScreenId(), PanelComponentTypes.CAROUSEL_TAB, 100, 700, 1000, 300, 0);

        screenBiz.publishScreen(screen.getScreenId(), context);

        ScreenLayoutConfig layout = screenBiz.getScreenLayout(screen.getScreenId(), context);

        assertNotNull(layout);
        assertEquals(6, layout.getWidgets().size());

        // 断言每类装饰/媒体 widget 经注册表校验通过且 componentType 透传正确
        for (ScreenLayoutConfig.Widget w : layout.getWidgets()) {
            boolean isDecorativeMedia = PanelComponentTypes.DECORATIVE_BORDER.equals(w.getComponentType())
                    || PanelComponentTypes.SCROLL_TEXT.equals(w.getComponentType())
                    || PanelComponentTypes.TIME_CLOCK.equals(w.getComponentType())
                    || PanelComponentTypes.VIDEO.equals(w.getComponentType())
                    || PanelComponentTypes.STREAM.equals(w.getComponentType())
                    || PanelComponentTypes.CAROUSEL_TAB.equals(w.getComponentType());
            assertTrue(isDecorativeMedia,
                    "widget should be a decorative/media type, got: " + w.getComponentType());
        }
    }

    /**
     * 接线验证（rule #23）：getComponentTypes API 经 PanelComponentRegistry 运行时连通。
     * 断言返回 14 类组件（8 既有 + 6 装饰/媒体），且新增 6 类均出现且 needsDataset=false。
     */
    @Test
    public void testGetComponentTypesReturnsAllFourteenIncludingDecorativeMedia() {
        IServiceContext context = newContext("e2e-user");

        java.util.List<PanelComponentMeta> types = screenBiz.getComponentTypes(context);

        assertNotNull(types);
        assertEquals(14, types.size(), "getComponentTypes should return 14 component types");

        // 新增 6 类装饰/媒体组件均出现且 needsDataset=false
        for (String decoType : new String[]{
                PanelComponentTypes.DECORATIVE_BORDER,
                PanelComponentTypes.SCROLL_TEXT,
                PanelComponentTypes.TIME_CLOCK,
                PanelComponentTypes.VIDEO,
                PanelComponentTypes.STREAM,
                PanelComponentTypes.CAROUSEL_TAB}) {
            PanelComponentMeta meta = types.stream()
                    .filter(m -> decoType.equals(m.getType()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(meta, "decorative/media type should be present in getComponentTypes: " + decoType);
            assertFalse(meta.isNeedsDataset(),
                    "decorative/media component should have needsDataset=false: " + decoType);
        }
    }

    // ==================== D4-4 发布生命周期增强：历史 + 缩略图 + 草稿预览 ====================

    /**
     * 历史浏览：多次 publish 后 getScreenSnapshotHistory 返回全部版本元信息（版本号递增、发布人/时间正确、不含 snapshotContent）。
     */
    @Test
    public void testGetScreenSnapshotHistoryReturnsAllVersions() {
        IServiceContext context = newContext("history-user");

        NopDatavScreen screen = saveScreen("screen-history", "history-screen");
        saveWidget("widget-h-1", screen.getScreenId(), "chart", 0, 0, 100, 100, 0);

        screenBiz.publishScreen(screen.getScreenId(), context);
        screenBiz.publishScreen(screen.getScreenId(), context);
        NopDatavScreenSnapshot snap3 = screenBiz.publishScreen(screen.getScreenId(), context);

        java.util.List<ScreenSnapshotHistory> history = screenBiz.getScreenSnapshotHistory(
                screen.getScreenId(), context);

        assertNotNull(history);
        assertEquals(3, history.size());

        // 按版本号倒序
        assertEquals(3L, history.get(0).getSnapshotVersion());
        assertEquals(2L, history.get(1).getSnapshotVersion());
        assertEquals(1L, history.get(2).getSnapshotVersion());

        // 最新版本元信息正确
        assertEquals(snap3.getPublishedBy(), history.get(0).getPublishedBy());
        assertEquals(snap3.getPublishedTime(), history.get(0).getPublishedTime());
        assertNotNull(history.get(0).getPublishedTime());

        // ScreenSnapshotHistory 无 snapshotContent 字段（DTO 只含元信息）
        // （断言 DTO 类型不暴露 snapshotContent——结构由 ScreenSnapshotHistory 类定义保证）
    }

    /**
     * 指定版本布局查看：getScreenLayoutByVersion 读历史版本解析正确；不存在版本 → ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND。
     */
    @Test
    public void testGetScreenLayoutByVersionReadsHistoricalLayout() {
        IServiceContext context = newContext("version-user");

        NopDatavScreen screen = saveScreen("screen-version", "version-screen");
        saveWidget("widget-v-1", screen.getScreenId(), "chart", 0, 0, 600, 400, 0);

        NopDatavScreenSnapshot snap1 = screenBiz.publishScreen(screen.getScreenId(), context);

        // 改 widget 配置后 publish v2
        NopDatavScreenWidget w = daoProvider.daoFor(NopDatavScreenWidget.class).getEntityById("widget-v-1");
        w.setW(800);
        daoProvider.daoFor(NopDatavScreenWidget.class).updateEntityDirectly(w);
        screenBiz.publishScreen(screen.getScreenId(), context);

        // 读 v1 布局（历史版本）
        ScreenLayoutConfig v1Layout = screenBiz.getScreenLayoutByVersion(
                screen.getScreenId(), snap1.getSnapshotVersion(), context);
        assertNotNull(v1Layout);
        assertEquals(1L, v1Layout.getSnapshotVersion());
        assertEquals(1, v1Layout.getWidgets().size());
        assertEquals(600, v1Layout.getWidgets().get(0).getW());

        // 读 v2 布局（最新）
        ScreenLayoutConfig v2Layout = screenBiz.getScreenLayoutByVersion(
                screen.getScreenId(), 2L, context);
        assertEquals(2L, v2Layout.getSnapshotVersion());
        assertEquals(800, v2Layout.getWidgets().get(0).getW());

        // 不存在版本 → 显式报错（rule #24 无静默跳过）
        NopException ex = assertThrows(NopException.class,
                () -> screenBiz.getScreenLayoutByVersion(screen.getScreenId(), 999L, context));
        assertEquals(ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 草稿预览（从未 publish 的大屏可预览）：getScreenDraftLayout 返回布局（含 widget 定位/组件类型）。
     * 验证经 parse(screenId, content) overload 实现（非独立第二套构建）——通过断言 widget 经 requireComponent
     * 校验通过 + 主题解析生效证明复用既有解析路径。
     */
    @Test
    public void testGetScreenDraftLayoutReturnsLayoutWithoutPublish() {
        IServiceContext context = newContext("draft-user");

        NopDatavScreen screen = saveScreen("screen-draft", "draft-screen");
        screen.setAdaptorMode(ScreenAdaptorMode.HEIGHT_FIRST);
        // backgroundConfig 含主题键，证明草稿预览复用 ScreenLayoutParser（D4-3 主题解析经同一路径生效）
        screen.setBackgroundConfig(JsonTool.stringify(Map.of("palette", Map.of("primary", "#FF0000"))));
        daoProvider.daoFor(NopDatavScreen.class).updateEntityDirectly(screen);
        saveWidget("widget-draft-1", screen.getScreenId(), "chart", 100, 200, 600, 400, 5);

        // 从未 publish 的大屏 → 草稿预览可工作（不抛 ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND）
        ScreenLayoutConfig draft = screenBiz.getScreenDraftLayout(screen.getScreenId(), context);

        assertNotNull(draft);
        assertEquals("screen-draft", draft.getScreenId());
        // 草稿无版本（snapshotVersion 保留默认 0）
        assertEquals(0L, draft.getSnapshotVersion());

        // Canvas 反映当前编辑态
        assertNotNull(draft.getCanvas());
        assertEquals(1920, draft.getCanvas().getWidth());
        assertEquals(ScreenAdaptorMode.HEIGHT_FIRST, draft.getCanvas().getAdaptorMode());

        // Widgets 反映当前编辑态
        assertEquals(1, draft.getWidgets().size());
        ScreenLayoutConfig.Widget w = draft.getWidgets().get(0);
        assertEquals("chart", w.getComponentType());
        assertEquals(100, w.getX());
        assertEquals(600, w.getW());

        // 接线验证：经 ScreenLayoutParser.parse(content overload) → D4-3 主题解析生效
        // （证明草稿预览复用既有解析路径，非独立第二套构建）
        assertNotNull(draft.getTheme());
        assertEquals("#FF0000", draft.getTheme().getPalette().get("primary"));
        // 未指定的命名色回退缺省
        assertEquals("#13C2C2", draft.getTheme().getPalette().get("secondary"));
    }

    /**
     * 草稿预览反映编辑态变更：编辑 widget 后再次预览，应反映新的 widget 定位。
     */
    @Test
    public void testGetScreenDraftLayoutReflectsEditChanges() {
        IServiceContext context = newContext("draft-edit-user");

        NopDatavScreen screen = saveScreen("screen-draft-edit", "draft-edit-screen");
        saveWidget("widget-edit", screen.getScreenId(), "chart", 0, 0, 300, 200, 0);

        // 第一次预览
        ScreenLayoutConfig draft1 = screenBiz.getScreenDraftLayout(screen.getScreenId(), context);
        assertEquals(300, draft1.getWidgets().get(0).getW());

        // 编辑 widget 配置（不 publish）
        NopDatavScreenWidget w = daoProvider.daoFor(NopDatavScreenWidget.class).getEntityById("widget-edit");
        w.setW(700);
        daoProvider.daoFor(NopDatavScreenWidget.class).updateEntityDirectly(w);

        // 再次预览 → 反映变更
        ScreenLayoutConfig draft2 = screenBiz.getScreenDraftLayout(screen.getScreenId(), context);
        assertEquals(700, draft2.getWidgets().get(0).getW());
    }

    /**
     * 缩略图：setScreenThumbnail 更新主表 thumbnail；publish 不改动 thumbnail（单一写入点验证）。
     */
    @Test
    public void testSetScreenThumbnailUpdatesMainTableOnly() {
        IServiceContext context = newContext("thumb-user");

        NopDatavScreen screen = saveScreen("screen-thumb", "thumb-screen");
        saveWidget("widget-thumb", screen.getScreenId(), "chart", 0, 0, 100, 100, 0);

        // 初始 thumbnail 为 null
        NopDatavScreen initial = daoProvider.daoFor(NopDatavScreen.class).getEntityById(screen.getScreenId());
        assertNull(initial.getThumbnail());

        // setScreenThumbnail 更新 thumbnail
        NopDatavScreen updated = screenBiz.setScreenThumbnail(
                screen.getScreenId(), "file-record-abc-123", context);
        assertNotNull(updated.getThumbnail());
        assertEquals("file-record-abc-123", updated.getThumbnail());

        // 重新加载确认持久化
        NopDatavScreen reloaded = daoProvider.daoFor(NopDatavScreen.class).getEntityById(screen.getScreenId());
        assertEquals("file-record-abc-123", reloaded.getThumbnail());

        // publish 后 thumbnail 不变（publish 不触碰 thumbnail 列）
        screenBiz.publishScreen(screen.getScreenId(), context);
        NopDatavScreen afterPublish = daoProvider.daoFor(NopDatavScreen.class).getEntityById(screen.getScreenId());
        assertEquals("file-record-abc-123", afterPublish.getThumbnail(),
                "publish must not modify thumbnail column (single writer: setScreenThumbnail only)");
    }

    /**
     * serializeScreenContent 只读附带当前 thumbnail 到快照 JSON（§12.7）：
     * 设置 thumbnail → publish → 快照 snapshotContent 含 thumbnail 字段。
     */
    @Test
    public void testSerializeScreenContentAttachesThumbnailReadOnly() {
        IServiceContext context = newContext("thumb-snap-user");

        NopDatavScreen screen = saveScreen("screen-thumb-snap", "thumb-snap-screen");
        saveWidget("widget-ts", screen.getScreenId(), "chart", 0, 0, 100, 100, 0);

        // 设置主表 thumbnail
        screen.setThumbnail("file-rec-xyz");
        daoProvider.daoFor(NopDatavScreen.class).updateEntityDirectly(screen);

        // publish → 快照 snapshotContent 应附带 thumbnail
        NopDatavScreenSnapshot snap = screenBiz.publishScreen(screen.getScreenId(), context);
        Map<String, Object> content = JsonTool.parseMap(snap.getSnapshotContent());
        assertNotNull(content.get("thumbnail"));
        assertEquals("file-rec-xyz", content.get("thumbnail"));

        // 验证 publish 未改动主表 thumbnail（单一写入点）
        NopDatavScreen afterPublish = daoProvider.daoFor(NopDatavScreen.class).getEntityById(screen.getScreenId());
        assertEquals("file-rec-xyz", afterPublish.getThumbnail());
    }

    /**
     * 端到端（rule #22）：创建大屏 → 配置 widget → getScreenDraftLayout 预览（未 publish）
     * → publish v1 → 改 widget → publish v2 → getScreenSnapshotHistory 列出 [v2,v1]
     * → getScreenLayoutByVersion(v1) 返回 v1 布局 → setScreenThumbnail → 断言全链路。
     */
    @Test
    public void testPublishLifecycleEndToEnd() {
        IServiceContext context = newContext("e2e-lifecycle-user");

        // 1. 创建大屏 + 配置 widget
        NopDatavScreen screen = saveScreen("screen-e2e-lifecycle", "e2e-lifecycle");
        saveWidget("widget-e2e-lc", screen.getScreenId(), "chart", 0, 0, 500, 300, 0);

        // 2. 草稿预览（未 publish）
        ScreenLayoutConfig draft = screenBiz.getScreenDraftLayout(screen.getScreenId(), context);
        assertNotNull(draft);
        assertEquals(1, draft.getWidgets().size());
        assertEquals(500, draft.getWidgets().get(0).getW());

        // 3. publish v1
        screenBiz.publishScreen(screen.getScreenId(), context);

        // 4. 改 widget → publish v2
        NopDatavScreenWidget w = daoProvider.daoFor(NopDatavScreenWidget.class).getEntityById("widget-e2e-lc");
        w.setW(900);
        daoProvider.daoFor(NopDatavScreenWidget.class).updateEntityDirectly(w);
        screenBiz.publishScreen(screen.getScreenId(), context);

        // 5. getScreenSnapshotHistory 列出 [v2, v1]
        java.util.List<ScreenSnapshotHistory> history = screenBiz.getScreenSnapshotHistory(
                screen.getScreenId(), context);
        assertEquals(2, history.size());
        assertEquals(2L, history.get(0).getSnapshotVersion());
        assertEquals(1L, history.get(1).getSnapshotVersion());

        // 6. getScreenLayoutByVersion(v1) 返回 v1 布局（w=500）
        ScreenLayoutConfig v1Layout = screenBiz.getScreenLayoutByVersion(
                screen.getScreenId(), 1L, context);
        assertEquals(1L, v1Layout.getSnapshotVersion());
        assertEquals(500, v1Layout.getWidgets().get(0).getW());

        // 7. setScreenThumbnail → 主表 thumbnail 更新
        NopDatavScreen withThumb = screenBiz.setScreenThumbnail(
                screen.getScreenId(), "thumb-rec-final", context);
        assertEquals("thumb-rec-final", withThumb.getThumbnail());
    }

    /**
     * plan 2255 聚焦断言：共享 ORM 会话（生产请求级 session 形态）内 setScreenThumbnail
     * 必须返回已更新实体。旧实现（raw SQL 绕过会话 + getEntityById 重读）在同会话下命中
     * 一级缓存返回旧实例（thumbnail/version 均旧）——该缺陷仅在共享会话下可观察，
     * 无环境 session 的直调用例（testSetScreenThumbnailUpdatesMainTableOnly）重读 DB 而掩盖。
     * 实体写实现返回的即会话内已改实体（thumbnail 新值 + version 递增）。
     */
    @Test
    public void testSetScreenThumbnailReturnsUpdatedEntityInSharedSession() {
        IServiceContext context = newContext("thumb-shared-user");
        NopDatavScreen screen = saveScreen("screen-thumb-shared", "thumb-shared-screen");

        IOrmTemplate orm = new OrmTemplateImpl(ormSessionFactory);
        orm.runInSession(s -> {
            NopDatavScreen before = daoProvider.daoFor(NopDatavScreen.class)
                    .getEntityById(screen.getScreenId());
            Long beforeVersion = before.getVersion();

            NopDatavScreen updated = screenBiz.setScreenThumbnail(
                    screen.getScreenId(), "file-record-shared-456", context);
            assertEquals("file-record-shared-456", updated.getThumbnail(),
                    "shared-session return must carry the new thumbnail (stale L1 cache in old raw-SQL impl)");
            assertTrue(updated.getVersion() > beforeVersion,
                    "shared-session return must carry the bumped version (old impl returned stale version)");

            return null;
        });

        // 会话外重读确认持久化
        NopDatavScreen reloaded = daoProvider.daoFor(NopDatavScreen.class)
                .getEntityById(screen.getScreenId());
        assertEquals("file-record-shared-456", reloaded.getThumbnail());
    }

    // ==================== Helpers ====================

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private NopDatavScreen saveScreen(String id, String name) {
        NopDatavScreen s = newScreen(id, name, 1920, 1080, ScreenAdaptorMode.FULL);
        daoProvider.daoFor(NopDatavScreen.class).saveEntityDirectly(s);
        return s;
    }

    private void saveWidget(String id, String screenId, String componentType,
                            int x, int y, int w, int h, int z) {
        NopDatavScreenWidget widget = newWidget(id, screenId, componentType, x, y, w, h, z);
        daoProvider.daoFor(NopDatavScreenWidget.class).saveEntityDirectly(widget);
    }

    private NopDatavScreen newScreen(String id, String name, int width, int height, int adaptorMode) {
        long now = System.currentTimeMillis();
        NopDatavScreen s = new NopDatavScreen();
        s.setScreenId(id);
        s.setScreenName(name);
        s.setDisplayName(name);
        s.setScreenWidth(width);
        s.setScreenHeight(height);
        s.setAdaptorMode(adaptorMode);
        s.setPublishStatus(0);
        s.setVersion(0L);
        s.setCreatedBy("test");
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy("test");
        s.setUpdateTime(new Timestamp(now));
        return s;
    }

    private NopDatavScreenWidget newWidget(String id, String screenId, String componentType,
                                           int x, int y, int w, int h, int z) {
        long now = System.currentTimeMillis();
        NopDatavScreenWidget widget = new NopDatavScreenWidget();
        widget.setWidgetId(id);
        widget.setScreenId(screenId);
        widget.setWidgetName(id);
        widget.setDisplayName(id);
        widget.setComponentType(componentType);
        widget.setX(x);
        widget.setY(y);
        widget.setW(w);
        widget.setH(h);
        widget.setZ(z);
        widget.setWidgetConfig(JsonTool.stringify(Map.of("option", Map.of("title", id))));
        widget.setVersion(0L);
        widget.setCreatedBy("test");
        widget.setCreateTime(new Timestamp(now));
        widget.setUpdatedBy("test");
        widget.setUpdateTime(new Timestamp(now));
        return widget;
    }
}
