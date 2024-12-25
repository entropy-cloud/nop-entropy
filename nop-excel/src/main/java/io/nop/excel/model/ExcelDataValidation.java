package io.nop.excel.model;

import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.CellRange;
import io.nop.excel.model._gen._ExcelDataValidation;

import java.util.Collections;
import java.util.List;

public class ExcelDataValidation extends _ExcelDataValidation {


    private List<CellRange> ranges;
    private List<String> listOptions;

    public ExcelDataValidation() {

    }

    public List<CellRange> getRanges() {
        if (ranges == null) {
            ranges = CellRange.parseRangeList(getSqref());
        }
        return ranges;
    }

    public void setRanges(List<CellRange> ranges) {
        this.ranges = ranges;
        if (ranges == null || ranges.isEmpty()) {
            setSqref(null);
        } else {
            setSqref(CellRange.toABStringList(ranges));
        }
    }

    @Override
    public void setSqref(String sqref) {
        this.ranges = null;
        super.setSqref(sqref);
    }

    public List<String> getListOptions() {
        if ("list".equals(getType())) {
            String formula = getFormula();
            if (StringHelper.isEmpty(formula))
                return Collections.emptyList();
            formula = StringHelper.unquote(formula);
            return StringHelper.split(formula, ',');
        }
        return null;
    }

    public void setListOptions(List<String> listOptions) {
        setType("list");
        String str = StringHelper.join(listOptions, ",");
        setFormula(StringHelper.quote(str));
    }
}
