//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast;
import io.nop.core.lang.ast.ASTNode;
import io.nop.api.core.ApiConstants;

public abstract class MermaidASTNode extends ASTNode<MermaidASTNode> {

	public abstract MermaidASTKind getASTKind();

	public String getASTType() {
		return getASTKind().toString();
	}

	public abstract MermaidASTNode deepClone();
}
