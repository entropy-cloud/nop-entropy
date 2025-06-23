package io.nop.ai.coder.orm;

import io.nop.ai.coder.utils.AiCoderHelper;
import io.nop.commons.text.CodeBuilder;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;

public class DictsModelToJava {

    public String buildJava(XNode dictsNode, String constantsClassName) {
        CodeBuilder cb = new CodeBuilder();
        cb.line("public interface {0} '{'", constantsClassName);
        cb.incIndent();

        if (dictsNode != null) {
            for (XNode dictNode : dictsNode.getChildren()) {
                appendDict(cb, dictNode);
            }
        }
        cb.decIndent().line("}");
        return cb.toString();
    }

    void appendDict(CodeBuilder cb, XNode dictNode) {
        String dictName = dictNode.attrText("name");
        String dictClassName = AiCoderHelper.camelCaseName(StringHelper.lastPart(dictName,'/'), true);
        cb.line("interface {0} '{'", dictClassName);
        cb.incIndent();
        dictNode.childrenByTag("option").forEach(child -> {
            String value = child.attrText("value");
            String code = child.attrText("code");
            String displayName = child.attrText("label");
            if(displayName == null)
                displayName = "";

            cb.line("String {0} = \"{1}\"; // {2}", code, value, displayName);
        });
        cb.decIndent();
        cb.line("}");
        cb.line();
    }
}