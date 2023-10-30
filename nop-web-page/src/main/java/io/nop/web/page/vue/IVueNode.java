package io.nop.web.page.vue;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast.Expression;

import java.util.List;

public interface IVueNode {
    SourceLocation getLocation();

    Expression getContent();

    List<VueNode> getChildren();
}
