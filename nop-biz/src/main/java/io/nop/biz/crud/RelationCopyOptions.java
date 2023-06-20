package io.nop.biz.crud;

public class RelationCopyOptions {
    private boolean allowAdd;
    private boolean allowUpdate;
    private boolean allowDelete;

    public RelationCopyOptions() {
    }

    public RelationCopyOptions(boolean allowAdd, boolean allowUpdate, boolean allowDelete) {
        this.allowAdd = allowAdd;
        this.allowUpdate = allowUpdate;
        this.allowDelete = allowDelete;
    }

    public boolean isAllowAdd() {
        return allowAdd;
    }

    public void setAllowAdd(boolean allowAdd) {
        this.allowAdd = allowAdd;
    }

    public boolean isAllowUpdate() {
        return allowUpdate;
    }

    public void setAllowUpdate(boolean allowUpdate) {
        this.allowUpdate = allowUpdate;
    }

    public boolean isAllowDelete() {
        return allowDelete;
    }

    public void setAllowDelete(boolean allowDelete) {
        this.allowDelete = allowDelete;
    }
}
