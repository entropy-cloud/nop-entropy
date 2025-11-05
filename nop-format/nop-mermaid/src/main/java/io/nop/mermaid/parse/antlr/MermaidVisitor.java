// Nop Generated from Mermaid.g4 by ANTLR 4.13.0
package io.nop.mermaid.parse.antlr;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link MermaidParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface MermaidVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidDocument}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidDocument(MermaidParser.MermaidDocumentContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidDiagramType_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidDiagramType_(MermaidParser.MermaidDiagramType_Context ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidStatements_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidStatements_(MermaidParser.MermaidStatements_Context ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidStatement(MermaidParser.MermaidStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidDirectionStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidDirectionStatement(MermaidParser.MermaidDirectionStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidDirection_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidDirection_(MermaidParser.MermaidDirection_Context ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidComment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidComment(MermaidParser.MermaidCommentContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidFlowNode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidFlowNode(MermaidParser.MermaidFlowNodeContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidNodeShape_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidNodeShape_(MermaidParser.MermaidNodeShape_Context ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidFlowEdge}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidFlowEdge(MermaidParser.MermaidFlowEdgeContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidEdgeType_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidEdgeType_(MermaidParser.MermaidEdgeType_Context ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidFlowSubgraph}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidFlowSubgraph(MermaidParser.MermaidFlowSubgraphContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidParticipant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidParticipant(MermaidParser.MermaidParticipantContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidSequenceMessage}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidSequenceMessage(MermaidParser.MermaidSequenceMessageContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidClassNode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidClassNode(MermaidParser.MermaidClassNodeContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidClassMembers_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidClassMembers_(MermaidParser.MermaidClassMembers_Context ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidClassMember}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidClassMember(MermaidParser.MermaidClassMemberContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidStateNode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidStateNode(MermaidParser.MermaidStateNodeContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidGanttTask}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidGanttTask(MermaidParser.MermaidGanttTaskContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidPieItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidPieItem(MermaidParser.MermaidPieItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidStyleStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidStyleStatement(MermaidParser.MermaidStyleStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidStyleAttributes_}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidStyleAttributes_(MermaidParser.MermaidStyleAttributes_Context ctx);
	/**
	 * Visit a parse tree produced by {@link MermaidParser#mermaidStyleAttribute}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMermaidStyleAttribute(MermaidParser.MermaidStyleAttributeContext ctx);
}