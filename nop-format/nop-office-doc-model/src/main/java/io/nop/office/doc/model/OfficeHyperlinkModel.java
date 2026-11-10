package io.nop.office.doc.model;

import io.nop.commons.collections.KeyedList;
import io.nop.office.doc.model._gen._OfficeHyperlinkModel;

public class OfficeHyperlinkModel extends _OfficeHyperlinkModel implements OfficeBlock{
    public OfficeHyperlinkModel(){

    }
    public OfficeHyperlinkTemplateModel makeModel() {
        OfficeHyperlinkTemplateModel model = getModel();
        if (model == null) {
            model = new OfficeHyperlinkTemplateModel();
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
