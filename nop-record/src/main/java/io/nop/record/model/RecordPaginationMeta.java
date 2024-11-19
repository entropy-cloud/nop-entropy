package io.nop.record.model;

import io.nop.record.model._gen._RecordPaginationMeta;

public class RecordPaginationMeta extends _RecordPaginationMeta {
    public RecordPaginationMeta() {

    }

    public void init(RecordDefinitions defs) {
        if (getPageHeader() != null)
            getPageHeader().init(defs);

        if (getPageFooter() != null)
            getPageFooter().init(defs);
    }
}
