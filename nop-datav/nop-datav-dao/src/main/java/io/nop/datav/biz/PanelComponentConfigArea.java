package io.nop.datav.biz;

import io.nop.api.core.exceptions.NopException;
import java.util.Objects;

/**
 * 组件配置区域描述符（D4-2）。描述某类组件 {@code widgetConfig} 中可出现的命名配置区域。
 *
 * <p>每个描述符含：名称（如 {@code src}）+ 用途说明 + 是否必填。
 * 描述符是机器可读的，供 {@code getComponentTypes} API 消费与测试断言；
 * 后端不做硬 JSON Schema 校验（向前兼容，见 {@code screen-design.md} §10.3）。</p>
 *
 * <p>位于 {@code nop-datav-dao} biz 包以保持依赖方向正确（接口 {@code INopDatavScreenBiz}
 * 的返回类型需在 dao 层；与 {@code ScreenLayoutConfig}/{@code PanelDataResult} 同模式）。</p>
 *
 * <p>不可变值对象。</p>
 */
public final class PanelComponentConfigArea {
    private final String name;
    private final String description;
    private final boolean required;

    public PanelComponentConfigArea(String name, String description, boolean required) {
        if (name == null || name.isEmpty()) {
            throw new NopException(NopDatavDaoErrors.ERR_DATAV_DAO_META_FIELD_MISSING).param(NopDatavDaoErrors.ARG_FIELD, "name");
        }
        if (description == null || description.isEmpty()) {
            throw new NopException(NopDatavDaoErrors.ERR_DATAV_DAO_META_FIELD_MISSING).param(NopDatavDaoErrors.ARG_FIELD, "description");
        }
        this.name = name;
        this.description = description;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PanelComponentConfigArea)) {
            return false;
        }
        PanelComponentConfigArea that = (PanelComponentConfigArea) o;
        return required == that.required
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, required);
    }

    @Override
    public String toString() {
        return "PanelComponentConfigArea{name='" + name + "', description='" + description
                + "', required=" + required + "}";
    }
}
