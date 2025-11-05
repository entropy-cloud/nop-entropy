//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast;

import io.nop.commons.functional.visit.AbstractVisitor;

// tell cpd to start ignoring code - CPD-OFF
public class MermaidASTVisitor extends AbstractVisitor<MermaidASTNode>{

    @Override
    public void visit(MermaidASTNode node){
        switch(node.getASTKind()){
        
                case MermaidDocument:
                    visitMermaidDocument((MermaidDocument)node);
                    return;
            
                case MermaidDirectionStatement:
                    visitMermaidDirectionStatement((MermaidDirectionStatement)node);
                    return;
            
                case MermaidComment:
                    visitMermaidComment((MermaidComment)node);
                    return;
            
                case MermaidFlowNode:
                    visitMermaidFlowNode((MermaidFlowNode)node);
                    return;
            
                case MermaidFlowEdge:
                    visitMermaidFlowEdge((MermaidFlowEdge)node);
                    return;
            
                case MermaidFlowSubgraph:
                    visitMermaidFlowSubgraph((MermaidFlowSubgraph)node);
                    return;
            
                case MermaidParticipant:
                    visitMermaidParticipant((MermaidParticipant)node);
                    return;
            
                case MermaidSequenceMessage:
                    visitMermaidSequenceMessage((MermaidSequenceMessage)node);
                    return;
            
                case MermaidClassNode:
                    visitMermaidClassNode((MermaidClassNode)node);
                    return;
            
                case MermaidClassMember:
                    visitMermaidClassMember((MermaidClassMember)node);
                    return;
            
                case MermaidStateNode:
                    visitMermaidStateNode((MermaidStateNode)node);
                    return;
            
                case MermaidGanttTask:
                    visitMermaidGanttTask((MermaidGanttTask)node);
                    return;
            
                case MermaidPieItem:
                    visitMermaidPieItem((MermaidPieItem)node);
                    return;
            
                case MermaidStyleStatement:
                    visitMermaidStyleStatement((MermaidStyleStatement)node);
                    return;
            
                case MermaidStyleAttribute:
                    visitMermaidStyleAttribute((MermaidStyleAttribute)node);
                    return;
            
        default:
        throw new IllegalArgumentException("invalid ast kind");
        }
    }

    
            public void visitMermaidDocument(MermaidDocument node){
            
                    this.visitChildren(node.getStatements());         
            }
        
            public void visitMermaidDirectionStatement(MermaidDirectionStatement node){
            
            }
        
            public void visitMermaidComment(MermaidComment node){
            
            }
        
            public void visitMermaidFlowNode(MermaidFlowNode node){
            
            }
        
            public void visitMermaidFlowEdge(MermaidFlowEdge node){
            
            }
        
            public void visitMermaidFlowSubgraph(MermaidFlowSubgraph node){
            
                    this.visitChildren(node.getStatements());         
            }
        
            public void visitMermaidParticipant(MermaidParticipant node){
            
            }
        
            public void visitMermaidSequenceMessage(MermaidSequenceMessage node){
            
            }
        
            public void visitMermaidClassNode(MermaidClassNode node){
            
                    this.visitChildren(node.getMembers());         
            }
        
            public void visitMermaidClassMember(MermaidClassMember node){
            
            }
        
            public void visitMermaidStateNode(MermaidStateNode node){
            
            }
        
            public void visitMermaidGanttTask(MermaidGanttTask node){
            
            }
        
            public void visitMermaidPieItem(MermaidPieItem node){
            
            }
        
            public void visitMermaidStyleStatement(MermaidStyleStatement node){
            
                    this.visitChildren(node.getAttributes());         
            }
        
            public void visitMermaidStyleAttribute(MermaidStyleAttribute node){
            
            }
        
}
// resume CPD analysis - CPD-ON
