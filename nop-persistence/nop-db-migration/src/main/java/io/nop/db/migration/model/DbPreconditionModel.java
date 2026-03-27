package io.nop.db.migration.model;

import io.nop.core.resource.component.AbstractComponentModel;

public abstract class DbPreconditionModel extends AbstractComponentModel {
    
    private String type;
    
    private String id;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        checkAllowChange();
        this.type = type;
    }

    public String getId() {
        return id;
    }

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
