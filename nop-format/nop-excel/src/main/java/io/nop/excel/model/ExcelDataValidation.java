package io.nop.excel.model;

import io.nop.api.core.beans.DictBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.CellRange;
import io.nop.excel.model._gen._ExcelDataValidation;
import io.nop.excel.model.constants.ExcelDataValidationType;
import io.nop.excel.model.constants.ExcelDataValidationOperator;
import io.nop.excel.model.constants.ExcelDataValidationErrorStyle;
import io.nop.excel.model.constants.ExcelDataValidationImeMode;

import java.util.Collections;
import java.util.List;

public class ExcelDataValidation extends _ExcelDataValidation {


    private List<CellRange> ranges;
    private List<String> listOptions;

    public ExcelDataValidation() {

    }

    public static ExcelDataValidation buildFromDict(DictBean dict, boolean useLabels) {
        ExcelDataValidation obj = new ExcelDataValidation();
        obj.setListOptions(useLabels ? dict.getLabels() : dict.getStringValues());
        return obj;
    }

    public ExcelDataValidation type(ExcelDataValidationType type) {
        setType(type);
        return this;
    }

    public ExcelDataValidation allowBlank(Boolean allowBlank) {
        setAllowBlank(allowBlank);
        return this;
    }

    public ExcelDataValidation formula1(String formula1) {
        setFormula1(formula1);
        return this;
    }

    public ExcelDataValidation formula2(String formula2) {
        setFormula2(formula2);
        return this;
    }

    public ExcelDataValidation id(String id) {
        setId(id);
        return this;
    }

    public ExcelDataValidation showErrorMessage(Boolean showErrorMessage) {
        setShowErrorMessage(showErrorMessage);
        return this;
    }

    public ExcelDataValidation showInputMessage(Boolean showInputMessage) {
        setShowInputMessage(showInputMessage);
        return this;
    }

    public ExcelDataValidation sqref(String sqref) {
        setSqref(sqref);
        return this;
    }

    public ExcelDataValidation ranges(List<CellRange> ranges) {
        setRanges(ranges);
        return this;
    }

    public ExcelDataValidation listOptions(List<String> listOptions) {
        setListOptions(listOptions);
        return this;
    }

    public ExcelDataValidation error(String error) {
        setError(error);
        return this;
    }

    public ExcelDataValidation errorStyle(ExcelDataValidationErrorStyle errorStyle) {
        setErrorStyle(errorStyle);
        return this;
    }

    public ExcelDataValidation errorTitle(String errorTitle) {
        setErrorTitle(errorTitle);
        return this;
    }

    public ExcelDataValidation imeMode(ExcelDataValidationImeMode imeMode) {
        setImeMode(imeMode);
        return this;
    }

    public ExcelDataValidation operator(ExcelDataValidationOperator operator) {
        setOperator(operator);
        return this;
    }

    public ExcelDataValidation prompt(String prompt) {
        setPrompt(prompt);
        return this;
    }

    public ExcelDataValidation promptTitle(String promptTitle) {
        setPromptTitle(promptTitle);
        return this;
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
        if (ExcelDataValidationType.LIST == getType()) {
            String formula = getFormula1();
            if (StringHelper.isEmpty(formula))
                return Collections.emptyList();
            formula = StringHelper.unquote(formula);
            return StringHelper.split(formula, ',');
        }
        return null;
    }

    public void setListOptions(List<String> listOptions) {
        setType(ExcelDataValidationType.LIST);
        String str = StringHelper.join(listOptions, ",");
        setFormula1(StringHelper.quote(str));
    }
}
