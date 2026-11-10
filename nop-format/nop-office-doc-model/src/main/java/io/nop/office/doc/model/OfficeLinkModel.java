package io.nop.office.doc.model;

import io.nop.commons.collections.KeyedList;
import io.nop.office.doc.model._gen._OfficeLinkModel;

public class OfficeLinkModel extends _OfficeLinkModel implements OfficeRun{
    public OfficeLinkModel(){

    }

    /**
     * Append a run to the {@code runs} KeyedList, lazily replacing the frozen empty
     * default with a mutable list.
     */
    @SuppressWarnings("unchecked")
    public void addRun(OfficeRun run) {
        KeyedList<OfficeRun> runs = (KeyedList<OfficeRun>) getRuns();
        if (runs.frozen()) {
            runs = new KeyedList<>(OfficeRun::getId);
            setRuns(runs);
        }
        runs.add(run);
    }
}
