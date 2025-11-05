//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast;

// tell cpd to start ignoring code - CPD-OFF
public class MermaidASTProcessor<T,C>{

    public T processAST(MermaidASTNode node, C context){
        if(node == null)
            return null;
       switch(node.getASTKind()){
    
            case MermaidDocument:
                return processMermaidDocument((MermaidDocument)node,context);
        
            case MermaidDirectionStatement:
                return processMermaidDirectionStatement((MermaidDirectionStatement)node,context);
        
            case MermaidComment:
                return processMermaidComment((MermaidComment)node,context);
        
            case MermaidFlowNode:
                return processMermaidFlowNode((MermaidFlowNode)node,context);
        
            case MermaidFlowEdge:
                return processMermaidFlowEdge((MermaidFlowEdge)node,context);
        
            case MermaidFlowSubgraph:
                return processMermaidFlowSubgraph((MermaidFlowSubgraph)node,context);
        
            case MermaidParticipant:
                return processMermaidParticipant((MermaidParticipant)node,context);
        
            case MermaidSequenceMessage:
                return processMermaidSequenceMessage((MermaidSequenceMessage)node,context);
        
            case MermaidClassNode:
                return processMermaidClassNode((MermaidClassNode)node,context);
        
            case MermaidClassMember:
                return processMermaidClassMember((MermaidClassMember)node,context);
        
            case MermaidStateNode:
                return processMermaidStateNode((MermaidStateNode)node,context);
        
            case MermaidGanttTask:
                return processMermaidGanttTask((MermaidGanttTask)node,context);
        
            case MermaidPieItem:
                return processMermaidPieItem((MermaidPieItem)node,context);
        
            case MermaidStyleStatement:
                return processMermaidStyleStatement((MermaidStyleStatement)node,context);
        
            case MermaidStyleAttribute:
                return processMermaidStyleAttribute((MermaidStyleAttribute)node,context);
        
          default:
             throw new IllegalArgumentException("invalid ast kind");
       }
    }

    
	public T processMermaidDocument(MermaidDocument node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidDirectionStatement(MermaidDirectionStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidComment(MermaidComment node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidFlowNode(MermaidFlowNode node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidFlowEdge(MermaidFlowEdge node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidFlowSubgraph(MermaidFlowSubgraph node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidParticipant(MermaidParticipant node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidSequenceMessage(MermaidSequenceMessage node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidClassNode(MermaidClassNode node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidClassMember(MermaidClassMember node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidStateNode(MermaidStateNode node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidGanttTask(MermaidGanttTask node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidPieItem(MermaidPieItem node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidStyleStatement(MermaidStyleStatement node, C context){
        return defaultProcess(node, context);
	}
    
	public T processMermaidStyleAttribute(MermaidStyleAttribute node, C context){
        return defaultProcess(node, context);
	}
    

    public T defaultProcess(MermaidASTNode node, C context){
        return null;
    }
}
// resume CPD analysis - CPD-ON
