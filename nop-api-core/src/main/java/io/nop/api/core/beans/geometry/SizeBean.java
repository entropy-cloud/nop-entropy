package io.nop.api.core.beans.geometry;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class SizeBean {
    private final double width;
    private final double height;

    public SizeBean(@JsonProperty("width") double width,
                    @JsonProperty("height") double height) {
        this.width = width;
        this.height = height;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public SizeBean scale(double scale) {
        return new SizeBean(width * scale, height * scale);
    }
}
