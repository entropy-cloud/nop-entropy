//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast;

import io.nop.core.lang.ast.optimize.AbstractOptimizer;

// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class MermaidASTOptimizer<C> extends AbstractOptimizer<MermaidASTNode,C>{

    public MermaidASTNode optimize(MermaidASTNode node,C context){
        switch(node.getASTKind()){
        
                case MermaidDocument:
                return optimizeMermaidDocument((MermaidDocument)node,context);
            
                case MermaidDirectionStatement:
                return optimizeMermaidDirectionStatement((MermaidDirectionStatement)node,context);
            
                case MermaidComment:
                return optimizeMermaidComment((MermaidComment)node,context);
            
                case MermaidFlowNode:
                return optimizeMermaidFlowNode((MermaidFlowNode)node,context);
            
                case MermaidFlowEdge:
                return optimizeMermaidFlowEdge((MermaidFlowEdge)node,context);
            
                case MermaidFlowSubgraph:
                return optimizeMermaidFlowSubgraph((MermaidFlowSubgraph)node,context);
            
                case MermaidParticipant:
                return optimizeMermaidParticipant((MermaidParticipant)node,context);
            
                case MermaidSequenceMessage:
                return optimizeMermaidSequenceMessage((MermaidSequenceMessage)node,context);
            
                case MermaidClassNode:
                return optimizeMermaidClassNode((MermaidClassNode)node,context);
            
                case MermaidClassMember:
                return optimizeMermaidClassMember((MermaidClassMember)node,context);
            
                case MermaidStateNode:
                return optimizeMermaidStateNode((MermaidStateNode)node,context);
            
                case MermaidGanttTask:
                return optimizeMermaidGanttTask((MermaidGanttTask)node,context);
            
                case MermaidPieItem:
                return optimizeMermaidPieItem((MermaidPieItem)node,context);
            
                case MermaidStyleStatement:
                return optimizeMermaidStyleStatement((MermaidStyleStatement)node,context);
            
                case MermaidStyleAttribute:
                return optimizeMermaidStyleAttribute((MermaidStyleAttribute)node,context);
            
        default:
        throw new IllegalArgumentException("invalid ast kind");
        }
    }

    
	public MermaidASTNode optimizeMermaidDocument(MermaidDocument node, C context){
        MermaidDocument ret = node;

        
                    if(node.getStatements() != null){
                    
                            java.util.List<io.nop.mermaid.ast.MermaidStatement> statementsOpt = optimizeList(node.getStatements(),true, context);
                            if(statementsOpt != node.getStatements()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(statementsOpt); ret = node.deepClone();}
                                ret.setStatements(statementsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidDirectionStatement(MermaidDirectionStatement node, C context){
        MermaidDirectionStatement ret = node;

        
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidComment(MermaidComment node, C context){
        MermaidComment ret = node;

        
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidFlowNode(MermaidFlowNode node, C context){
        MermaidFlowNode ret = node;

        
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidFlowEdge(MermaidFlowEdge node, C context){
        MermaidFlowEdge ret = node;

        
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidFlowSubgraph(MermaidFlowSubgraph node, C context){
        MermaidFlowSubgraph ret = node;

        
                    if(node.getStatements() != null){
                    
                            java.util.List<io.nop.mermaid.ast.MermaidStatement> statementsOpt = optimizeList(node.getStatements(),true, context);
                            if(statementsOpt != node.getStatements()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(statementsOpt); ret = node.deepClone();}
                                ret.setStatements(statementsOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidParticipant(MermaidParticipant node, C context){
        MermaidParticipant ret = node;

        
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidSequenceMessage(MermaidSequenceMessage node, C context){
        MermaidSequenceMessage ret = node;

        
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidClassNode(MermaidClassNode node, C context){
        MermaidClassNode ret = node;

        
                    if(node.getMembers() != null){
                    
                            java.util.List<io.nop.mermaid.ast.MermaidClassMember> membersOpt = optimizeList(node.getMembers(),true, context);
                            if(membersOpt != node.getMembers()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(membersOpt); ret = node.deepClone();}
                                ret.setMembers(membersOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidClassMember(MermaidClassMember node, C context){
        MermaidClassMember ret = node;

        
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidStateNode(MermaidStateNode node, C context){
        MermaidStateNode ret = node;

        
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidGanttTask(MermaidGanttTask node, C context){
        MermaidGanttTask ret = node;

        
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidPieItem(MermaidPieItem node, C context){
        MermaidPieItem ret = node;

        
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidStyleStatement(MermaidStyleStatement node, C context){
        MermaidStyleStatement ret = node;

        
                    if(node.getAttributes() != null){
                    
                            java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> attributesOpt = optimizeList(node.getAttributes(),true, context);
                            if(attributesOpt != node.getAttributes()){
                                incChangeCount();
                                if(shouldClone(ret,node))  { clearParent(attributesOpt); ret = node.deepClone();}
                                ret.setAttributes(attributesOpt);
                            }
                        
                    }
                
		return ret;
	}
    
	public MermaidASTNode optimizeMermaidStyleAttribute(MermaidStyleAttribute node, C context){
        MermaidStyleAttribute ret = node;

        
		return ret;
	}
    
}
// resume CPD analysis - CPD-ON
