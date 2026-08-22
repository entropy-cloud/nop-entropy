package io.nop.table.validator.compile;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.validator.ModelBasedValidator;
import io.nop.core.model.validator.ValidatorCheckModel;
import io.nop.core.model.validator.ValidatorModel;
import io.nop.table.validator.model.*;

import java.util.ArrayList;
import java.util.List;

public class TableValidatorCompiler {

    public TableValidatorCompiled compile(TableValidatorModel model) {
        String description = model.getDescription();
        ModelBasedValidator[] rowValidators = compileRowValidators(model.getRowValidators());
        TableValidatorCompiled.CompiledStatCheck[] statChecks = compileStatChecks(model.getStatChecks());
        TableValidatorCompiled.CompiledTableCheck[] tableChecks = compileTableChecks(model.getTableChecks());
        return new TableValidatorCompiled(description, rowValidators, statChecks, tableChecks);
    }

    private ModelBasedValidator[] compileRowValidators(List<RowValidatorDef> defs) {
        if (defs == null || defs.isEmpty())
            return null;

        ModelBasedValidator[] arr = new ModelBasedValidator[defs.size()];
        for (int i = 0; i < defs.size(); i++) {
            arr[i] = new ModelBasedValidator(compileRowValidator(defs.get(i)));
        }
        return arr;
    }

    private ValidatorModel compileRowValidator(RowValidatorDef def) {
        ValidatorModel vm = new ValidatorModel();
        var vNode = def.getValidator();
        if (vNode == null)
            return vm;

        for (var checkNode : vNode.getChildren()) {
            if (!"check".equals(checkNode.getTagName()))
                continue;
            ValidatorCheckModel check = new ValidatorCheckModel();
            check.setId((String) checkNode.getAttr("id"));
            check.setErrorCode((String) checkNode.getAttr("errorCode"));
            String sev = (String) checkNode.getAttr("severity");
            if (sev != null) {
                try {
                    check.setSeverity(Integer.parseInt(sev));
                } catch (NumberFormatException ignored) {
                    // non-numeric severity, use default
                }
            }
            check.setErrorDescription((String) checkNode.getAttr("errorDescription"));
            var condition = checkNode.childByTag("condition");
            if (condition != null)
                check.setCondition(unwrapCondition(condition));
            vm.addCheck(check);
        }
        return vm;
    }

    /**
     * condition 是 XML 中的包裹节点（<condition><gt .../></condition>），而 FilterBeanEvaluator
     * 要求根节点本身是合法的过滤算子。单个子节点时直接解包；多个子节点按 AND 语义求值
     */
    private static XNode unwrapCondition(XNode condition) {
        List<XNode> children = condition.getChildren();
        if (children == null || children.isEmpty())
            return condition;
        if (children.size() == 1)
            return children.get(0);
        XNode and = XNode.make(FilterBeanConstants.FILTER_OP_AND);
        and.appendChildren(children);
        return and;
    }

    private TableValidatorCompiled.CompiledStatCheck[] compileStatChecks(List<TableStatCheckModel> checks) {
        if (checks == null || checks.isEmpty())
            return null;

        TableValidatorCompiled.CompiledStatCheck[] arr =
                new TableValidatorCompiled.CompiledStatCheck[checks.size()];
        for (int i = 0; i < checks.size(); i++) {
            TableStatCheckModel check = checks.get(i);
            arr[i] = new TableValidatorCompiled.CompiledStatCheck(
                    check.getColumn(),
                    check.getErrorCode(),
                    check.getSeverity(),
                    check.getErrorDescription(),
                    check.getErrorParams(),
                    buildFilter(check)
            );
        }
        return arr;
    }

    private ITreeBean buildFilter(TableStatCheckModel check) {
        if (check.getCondition() != null)
            return check.getCondition();

        if (check.getGeValue() != null)
            return new TreeBean(FilterBeanConstants.FILTER_OP_GE)
                    .attr("name", "value").attr("value", check.getGeValue());
        if (check.getLeValue() != null)
            return new TreeBean(FilterBeanConstants.FILTER_OP_LE)
                    .attr("name", "value").attr("value", check.getLeValue());
        if (check.getGtValue() != null)
            return new TreeBean(FilterBeanConstants.FILTER_OP_GT)
                    .attr("name", "value").attr("value", check.getGtValue());
        if (check.getLtValue() != null)
            return new TreeBean(FilterBeanConstants.FILTER_OP_LT)
                    .attr("name", "value").attr("value", check.getLtValue());
        if (check.getBetweenMin() != null || check.getBetweenMax() != null) {
            TreeBean bean = new TreeBean(FilterBeanConstants.FILTER_OP_BETWEEN)
                    .attr("name", "value");
            if (check.getBetweenMin() != null)
                bean.setAttr("min", check.getBetweenMin());
            if (check.getBetweenMax() != null)
                bean.setAttr("max", check.getBetweenMax());
            return bean;
        }
        return null;
    }

    private TableValidatorCompiled.CompiledTableCheck[] compileTableChecks(List<TableGlobalCheckModel> checks) {
        if (checks == null || checks.isEmpty())
            return null;

        TableValidatorCompiled.CompiledTableCheck[] arr =
                new TableValidatorCompiled.CompiledTableCheck[checks.size()];
        for (int i = 0; i < checks.size(); i++) {
            TableGlobalCheckModel check = checks.get(i);
            arr[i] = new TableValidatorCompiled.CompiledTableCheck(
                    check.getErrorCode(),
                    check.getSeverity(),
                    check.getErrorDescription(),
                    check.getErrorParams(),
                    check.getRowCountMin(), check.getRowCountMax(),
                    check.getColumnCountMin(), check.getColumnCountMax(),
                    check.getCondition()
            );
        }
        return arr;
    }
}
