package io.nop.report.spl.model;

import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;

public class SplModel implements IComponentModel {
    private SourceLocation location;
    private String source;

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}