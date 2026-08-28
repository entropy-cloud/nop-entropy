package io.nop.web.page;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.GlobalInstance;

import java.util.List;

/**
 * 扩展元数据，对应extension.json的结构
 *
 * <p>字段集合与 nop-chaos-next {@code packages/shared/src/types/extension.ts} 的
 * {@code ExtensionManifest} 对齐：
 * {@code id} / {@code name} / {@code version?} / {@code description?} / {@code entry} /
 * {@code styleAssets?} / {@code assets?}。</p>
 */
@GlobalInstance
public class ExtensionMeta {
    @Description("扩展唯一标识")
    private String id;

    @Description("扩展显示名称")
    private String name;

    @Description("扩展版本号")
    private String version;

    @Description("扩展描述")
    private String description;

    @Description("入口JS文件路径（相对于扩展目录）")
    private String entry;

    @Description("样式文件路径列表（相对于扩展目录）")
    private List<String> styleAssets;

    @Description("非 CSS 静态资源路径列表，如 SVG/字体/JSON（相对于扩展目录）")
    private List<String> assets;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEntry() {
        return entry;
    }

    public void setEntry(String entry) {
        this.entry = entry;
    }

    public List<String> getStyleAssets() {
        return styleAssets;
    }

    public void setStyleAssets(List<String> styleAssets) {
        this.styleAssets = styleAssets;
    }

    public List<String> getAssets() {
        return assets;
    }

    public void setAssets(List<String> assets) {
        this.assets = assets;
    }
}