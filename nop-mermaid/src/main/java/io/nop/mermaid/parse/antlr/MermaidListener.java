// Nop Generated from Mermaid.g4 by ANTLR 4.13.0
package io.nop.mermaid.parse.antlr;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link MermaidParser}.
 */
public interface MermaidListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidDocument}.
	 * @param ctx the parse tree
	 */
	void enterMermaidDocument(MermaidParser.MermaidDocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidDocument}.
	 * @param ctx the parse tree
	 */
	void exitMermaidDocument(MermaidParser.MermaidDocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidDiagramType_}.
	 * @param ctx the parse tree
	 */
	void enterMermaidDiagramType_(MermaidParser.MermaidDiagramType_Context ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidDiagramType_}.
	 * @param ctx the parse tree
	 */
	void exitMermaidDiagramType_(MermaidParser.MermaidDiagramType_Context ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidStatements_}.
	 * @param ctx the parse tree
	 */
	void enterMermaidStatements_(MermaidParser.MermaidStatements_Context ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidStatements_}.
	 * @param ctx the parse tree
	 */
	void exitMermaidStatements_(MermaidParser.MermaidStatements_Context ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidStatement}.
	 * @param ctx the parse tree
	 */
	void enterMermaidStatement(MermaidParser.MermaidStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidStatement}.
	 * @param ctx the parse tree
	 */
	void exitMermaidStatement(MermaidParser.MermaidStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidDirectionStatement}.
	 * @param ctx the parse tree
	 */
	void enterMermaidDirectionStatement(MermaidParser.MermaidDirectionStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidDirectionStatement}.
	 * @param ctx the parse tree
	 */
	void exitMermaidDirectionStatement(MermaidParser.MermaidDirectionStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidDirection_}.
	 * @param ctx the parse tree
	 */
	void enterMermaidDirection_(MermaidParser.MermaidDirection_Context ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidDirection_}.
	 * @param ctx the parse tree
	 */
	void exitMermaidDirection_(MermaidParser.MermaidDirection_Context ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidComment}.
	 * @param ctx the parse tree
	 */
	void enterMermaidComment(MermaidParser.MermaidCommentContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidComment}.
	 * @param ctx the parse tree
	 */
	void exitMermaidComment(MermaidParser.MermaidCommentContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidFlowNode}.
	 * @param ctx the parse tree
	 */
	void enterMermaidFlowNode(MermaidParser.MermaidFlowNodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidFlowNode}.
	 * @param ctx the parse tree
	 */
	void exitMermaidFlowNode(MermaidParser.MermaidFlowNodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidNodeShape_}.
	 * @param ctx the parse tree
	 */
	void enterMermaidNodeShape_(MermaidParser.MermaidNodeShape_Context ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidNodeShape_}.
	 * @param ctx the parse tree
	 */
	void exitMermaidNodeShape_(MermaidParser.MermaidNodeShape_Context ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidFlowEdge}.
	 * @param ctx the parse tree
	 */
	void enterMermaidFlowEdge(MermaidParser.MermaidFlowEdgeContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidFlowEdge}.
	 * @param ctx the parse tree
	 */
	void exitMermaidFlowEdge(MermaidParser.MermaidFlowEdgeContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidEdgeType_}.
	 * @param ctx the parse tree
	 */
	void enterMermaidEdgeType_(MermaidParser.MermaidEdgeType_Context ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidEdgeType_}.
	 * @param ctx the parse tree
	 */
	void exitMermaidEdgeType_(MermaidParser.MermaidEdgeType_Context ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidFlowSubgraph}.
	 * @param ctx the parse tree
	 */
	void enterMermaidFlowSubgraph(MermaidParser.MermaidFlowSubgraphContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidFlowSubgraph}.
	 * @param ctx the parse tree
	 */
	void exitMermaidFlowSubgraph(MermaidParser.MermaidFlowSubgraphContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidParticipant}.
	 * @param ctx the parse tree
	 */
	void enterMermaidParticipant(MermaidParser.MermaidParticipantContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidParticipant}.
	 * @param ctx the parse tree
	 */
	void exitMermaidParticipant(MermaidParser.MermaidParticipantContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidSequenceMessage}.
	 * @param ctx the parse tree
	 */
	void enterMermaidSequenceMessage(MermaidParser.MermaidSequenceMessageContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidSequenceMessage}.
	 * @param ctx the parse tree
	 */
	void exitMermaidSequenceMessage(MermaidParser.MermaidSequenceMessageContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidClassNode}.
	 * @param ctx the parse tree
	 */
	void enterMermaidClassNode(MermaidParser.MermaidClassNodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidClassNode}.
	 * @param ctx the parse tree
	 */
	void exitMermaidClassNode(MermaidParser.MermaidClassNodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidClassMembers_}.
	 * @param ctx the parse tree
	 */
	void enterMermaidClassMembers_(MermaidParser.MermaidClassMembers_Context ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidClassMembers_}.
	 * @param ctx the parse tree
	 */
	void exitMermaidClassMembers_(MermaidParser.MermaidClassMembers_Context ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidClassMember}.
	 * @param ctx the parse tree
	 */
	void enterMermaidClassMember(MermaidParser.MermaidClassMemberContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidClassMember}.
	 * @param ctx the parse tree
	 */
	void exitMermaidClassMember(MermaidParser.MermaidClassMemberContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidStateNode}.
	 * @param ctx the parse tree
	 */
	void enterMermaidStateNode(MermaidParser.MermaidStateNodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidStateNode}.
	 * @param ctx the parse tree
	 */
	void exitMermaidStateNode(MermaidParser.MermaidStateNodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidGanttTask}.
	 * @param ctx the parse tree
	 */
	void enterMermaidGanttTask(MermaidParser.MermaidGanttTaskContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidGanttTask}.
	 * @param ctx the parse tree
	 */
	void exitMermaidGanttTask(MermaidParser.MermaidGanttTaskContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidPieItem}.
	 * @param ctx the parse tree
	 */
	void enterMermaidPieItem(MermaidParser.MermaidPieItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidPieItem}.
	 * @param ctx the parse tree
	 */
	void exitMermaidPieItem(MermaidParser.MermaidPieItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidStyleStatement}.
	 * @param ctx the parse tree
	 */
	void enterMermaidStyleStatement(MermaidParser.MermaidStyleStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidStyleStatement}.
	 * @param ctx the parse tree
	 */
	void exitMermaidStyleStatement(MermaidParser.MermaidStyleStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidStyleAttributes_}.
	 * @param ctx the parse tree
	 */
	void enterMermaidStyleAttributes_(MermaidParser.MermaidStyleAttributes_Context ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidStyleAttributes_}.
	 * @param ctx the parse tree
	 */
	void exitMermaidStyleAttributes_(MermaidParser.MermaidStyleAttributes_Context ctx);
	/**
	 * Enter a parse tree produced by {@link MermaidParser#mermaidStyleAttribute}.
	 * @param ctx the parse tree
	 */
	void enterMermaidStyleAttribute(MermaidParser.MermaidStyleAttributeContext ctx);
	/**
	 * Exit a parse tree produced by {@link MermaidParser#mermaidStyleAttribute}.
	 * @param ctx the parse tree
	 */
	void exitMermaidStyleAttribute(MermaidParser.MermaidStyleAttributeContext ctx);
}