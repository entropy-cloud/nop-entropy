package io.nop.ai.core.api.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = Content.TextContent.class, name = "text"),
        @JsonSubTypes.Type(value = Content.ImageContent.class, name = "image"),
        @JsonSubTypes.Type(value = Content.EmbeddedResource.class, name = "resource")})
public interface Content {

    default String type() {
        if (this instanceof TextContent) {
            return "text";
        } else if (this instanceof ImageContent) {
            return "image";
        } else if (this instanceof EmbeddedResource) {
            return "resource";
        }
        throw new IllegalArgumentException("Unknown content type: " + this);
    }

    @DataBean
    class TextContent implements Content { // @formatter:on

        private final String text;

        public TextContent(
                @JsonProperty("text") String text) {
            this.text = text;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getText() {
            return text;
        }
    }

    @DataBean
    class ImageContent implements Content {
        private final List<String> audience;
        private final Double priority;
        private final String data;
        private final String mimeType;

        public ImageContent(
                @JsonProperty("audience") List<String> audience,
                @JsonProperty("priority") Double priority,
                @JsonProperty("data") String data,
                @JsonProperty("mimeType") String mimeType) {
            this.audience = audience;
            this.priority = priority;
            this.data = data;
            this.mimeType = mimeType;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<String> getAudience() {
            return audience;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public Double getPriority() {
            return priority;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getData() {
            return data;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getMimeType() {
            return mimeType;
        }
    }

    @DataBean
    class EmbeddedResource implements Content {
        private final List<String> audience;
        private final Double priority;
        private final ResourceContents resource;

        public EmbeddedResource(
                @JsonProperty("audience") List<String> audience,
                @JsonProperty("priority") Double priority,
                @JsonProperty("resource") ResourceContents resource) { // @formatter:on
            this.audience = audience;
            this.priority = priority;
            this.resource = resource;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<String> getAudience() {
            return audience;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public Double getPriority() {
            return priority;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public ResourceContents getResource() {
            return resource;
        }
    }

    @DataBean
    class ResourceContents {
        private String url;
        private String mimeType;
        private String text;
        private String blob;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getBlob() {
            return blob;
        }

        public void setBlob(String blob) {
            this.blob = blob;
        }
    }
}