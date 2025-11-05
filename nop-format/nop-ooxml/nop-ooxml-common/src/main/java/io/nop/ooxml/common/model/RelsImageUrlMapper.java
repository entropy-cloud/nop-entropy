package io.nop.ooxml.common.model;

import io.nop.commons.util.StringHelper;

public class RelsImageUrlMapper implements ImageUrlMapper {
    private final String baseUrl;
    private final OfficeRelsPart relsPart;

    public RelsImageUrlMapper(OfficeRelsPart relsPart, String baseUrl) {
        this.baseUrl = baseUrl;
        this.relsPart = relsPart;
    }

    @Override
    public String getImageUrl(String rId) {
        String target = relsPart.getTarget(rId);
        return StringHelper.appendPath(baseUrl, target);
    }
}