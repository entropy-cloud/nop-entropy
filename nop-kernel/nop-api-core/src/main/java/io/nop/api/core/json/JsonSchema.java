package io.nop.api.core.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.Map;
import java.util.Set;

@DataBean
public class JsonSchema extends JsonExtensibleNode {
    private String id;
    private String title;
    private String description;
    private String type;
    private Map<String, JsonSchema> properties; // 直接使用JsonSchema作为属性类型
    private Set<String> required;
    private JsonSchema items; // 用于数组类型
    private Boolean additionalProperties;
    private List<Object> _enum;
    private Object _default;

    // 原JsonSchemaProperty的字段
    private String format;
    private Object minimum;
    private Object maximum;
    private Integer minLength;
    private Integer maxLength;
    private String pattern;

    public String toJson() {
        return JSON.stringify(this);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, JsonSchema> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, JsonSchema> properties) {
        this.properties = properties;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Set<String> getRequired() {
        return required;
    }

    public void setRequired(Set<String> required) {
        this.required = required;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public JsonSchema getItems() {
        return items;
    }

    public void setItems(JsonSchema items) {
        this.items = items;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Boolean additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<Object> getEnum() {
        return _enum;
    }

    public void setEnum(List<Object> enumValues) {
        this._enum = enumValues;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getDefault() {
        return _default;
    }

    public void setDefault(Object defaultValue) {
        this._default = defaultValue;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getMinimum() {
        return minimum;
    }

    public void setMinimum(Object minimum) {
        this.minimum = minimum;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getMaximum() {
        return maximum;
    }

    public void setMaximum(Object maximum) {
        this.maximum = maximum;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}