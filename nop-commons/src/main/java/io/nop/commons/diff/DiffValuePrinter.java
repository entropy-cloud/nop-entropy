package io.nop.commons.diff;

import io.nop.commons.text.IndentPrinter;
import io.nop.commons.util.StringHelper;

import java.util.List;
import java.util.Map;

public class DiffValuePrinter {

    public String print(IDiffValue diff) {
        IndentPrinter out = new IndentPrinter(1000);
        print(diff, out);
        return out.toString();
    }

    public void print(IDiffValue diff, IndentPrinter out) {
        DiffType diffType = diff.getDiffType();
        Map<String, ? extends IDiffValue> propDiffs = diff.getPropDiffs();
        List<? extends IDiffValue> elementsDiff = diff.getElementDiffs();

        out.append('@').append(diffType.name()).append(": ");
        out.incIndent();

        if (propDiffs != null && !propDiffs.isEmpty()) {
            propDiffs.forEach((name, value) -> {
                out.indent().append(name).append(" => ");
                out.incIndent();
                print(value, out);
                out.decIndent();
            });
        } else if (elementsDiff != null && !elementsDiff.isEmpty()) {
            elementsDiff.forEach(elm -> {
                out.indent().append("-").incIndent();
                print(elm, out);
                out.decIndent();
            });
        } else {
            out.append("old=").append(limitValue(diff.getOldValue()))
                    .append(",new=").append(limitValue(diff.getNewValue()));
        }


        out.decIndent();
    }

    private String limitValue(Object value) {
        String text = String.valueOf(value);
        return StringHelper.limitLen(text, 50);
    }
}
