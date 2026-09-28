
package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 跨类型字段解析结果 DTO（来源：{@code NopMetaEntityBizModel.resolveEntityFields}）。
 */
@DataBean
public class ResolveEntityFieldsResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String entityKind;
    private List<ResolvedEntityFieldDTO> fields = new ArrayList<>();

    public String getEntityKind() {
        return entityKind;
    }

    public void setEntityKind(String entityKind) {
        this.entityKind = entityKind;
    }

    public List<ResolvedEntityFieldDTO> getFields() {
        return fields;
    }

    public void setFields(List<ResolvedEntityFieldDTO> fields) {
        this.fields = fields;
    }
}
