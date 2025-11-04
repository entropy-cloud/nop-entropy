package io.nop.api.core.beans.geometry;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class RectangleBean {
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public RectangleBean(@JsonProperty("x") double x,
                         @JsonProperty("y") double y,
                         @JsonProperty("width") double width,
                         @JsonProperty("height") double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getEndX() {
        return x + width;
    }

    public double getEndY() {
        return y + height;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public RectangleBean scale(double scale) {
        return new RectangleBean(x * scale, y * scale, width * scale, height * scale);
    }

    public RectangleBean translate(double dx, double dy) {
        return new RectangleBean(x + dx, y + dy, width, height);
    }

    public SizeBean getSize() {
        return new SizeBean(width, height);
    }

    public boolean contains(double px, double py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }
}