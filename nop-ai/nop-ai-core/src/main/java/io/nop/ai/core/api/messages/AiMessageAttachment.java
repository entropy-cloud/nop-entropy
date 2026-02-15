package io.nop.ai.core.api.messages;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.BinaryDataBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.util.HashMap;
import java.util.Map;

import static io.nop.ai.core.AiCoreConstants.RESOURCE_TYPE_AUDIO;
import static io.nop.ai.core.AiCoreConstants.RESOURCE_TYPE_IMAGE;

/**
 * @deprecated This internal AI core class is deprecated and will be removed in future versions.
 * Please use the new AI API instead.
 */
@DataBean
@Deprecated
public class AiMessageAttachment {
    private String resourceType;
    private String resourceUrl;

    public static AiMessageAttachment forImage(IResource resource) {
        AiMessageAttachment ret = new AiMessageAttachment();
        ret.setResourceType(RESOURCE_TYPE_IMAGE);
        ret.setResource(resource);
        return ret;
    }

    public static AiMessageAttachment fromBinaryData(BinaryDataBean bean) {
        AiMessageAttachment link = new AiMessageAttachment();
        link.setResourceType(StringHelper.firstPart(bean.getContentType(), '/'));
        link.setResourceUrl("data:" + bean.getContentType() + ";base64," + StringHelper.encodeBase64(bean.getData()));
        return link;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceUrl() {
        return resourceUrl;
    }

    public void setResourceUrl(String resourceUrl) {
        this.resourceUrl = resourceUrl;
    }


    public void setResource(IResource resource) {
        this.resourceUrl = getEncodedUrl(resource);
    }

    public Map<String, Object> toContent() {
        Map<String, Object> ret = new HashMap<>();
        if (RESOURCE_TYPE_IMAGE.equals(getResourceType())) {
            ret.put("type", "image_url");
            ret.put("image_url", Map.of("url", getResourceUrl()));
        } else if (RESOURCE_TYPE_AUDIO.equals(getResourceType())) {
            ret.put("type", "audio_url");
            ret.put("audio_url", Map.of("url", getResourceUrl()));
        }
        return ret;
    }

    public static String getEncodedUrl(IResource resource) {
        String base64 = StringHelper.encodeBase64(ResourceHelper.readBytes(resource));
        String fileExt = StringHelper.fileExt(resource.getPath());
        return "data:" + getMimeType(fileExt) + ";base64," + base64;
    }

    static String getMimeType(String fileExt) {
        if (fileExt.equalsIgnoreCase("png"))
            return "image/png";
        if (fileExt.equalsIgnoreCase("jpg") || fileExt.equalsIgnoreCase("jpeg"))
            return "image/jpeg";
        if (fileExt.equalsIgnoreCase("mp3"))
            return "audio/mp3";
        return "application/oct-stream";
    }
}