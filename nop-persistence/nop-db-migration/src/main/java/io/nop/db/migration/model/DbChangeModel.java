package io.nop.db.migration.model;

import io.nop.core.resource.component.AbstractComponentModel;

public abstract class DbChangeModel extends AbstractComponentModel implements IDbChange {
    
    private String type;
    
    private String id;

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void setType(String type) {
        checkAllowChange();
        this.type = type;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        checkAllowChange();
        this.id = id;
    }
    
    @Override
    protected void outputJson(io.nop.core.lang.json.IJsonHandler handler) {
        super.outputJson(handler);
        handler.putNotNull("type", this.getType());
        handler.putNotNull("id", this.getId());
    }
}
