package io.nop.office.doc.model;

import io.nop.commons.collections.KeyedList;
import io.nop.office.doc.model._gen._OfficeTitleModel;

public class OfficeTitleModel extends _OfficeTitleModel implements OfficeBlock{
    public OfficeTitleModel(){

    }
    public OfficeTitleTemplateModel makeModel() {
        OfficeTitleTemplateModel model = getModel();
        if (model == null) {
            model = new OfficeTitleTemplateModel();
            model.setLocation(getLocation());
            setModel(model);
        }
        return model;
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
