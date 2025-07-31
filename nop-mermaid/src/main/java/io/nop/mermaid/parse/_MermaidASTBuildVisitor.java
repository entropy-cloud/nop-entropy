
package io.nop.mermaid.parse;

import io.nop.mermaid.parse.antlr.MermaidBaseVisitor;
import io.nop.mermaid.parse.antlr.MermaidParser.*;
import io.nop.api.core.exceptions.NopException;  //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.util.SourceLocation; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.commons.util.CollectionHelper;//NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.commons.util.StringHelper;//NOPMD - suppressed UnusedImports - Auto Gen Code
import org.antlr.v4.runtime.tree.ParseTree;
import io.nop.antlr4.common.ParseTreeHelper;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import io.nop.mermaid.ast.MermaidASTNode;



// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName","java:S116"})
public abstract class _MermaidASTBuildVisitor extends MermaidBaseVisitor<MermaidASTNode>{

      public io.nop.mermaid.ast.MermaidClassMember visitMermaidClassMember(MermaidClassMemberContext ctx){
          io.nop.mermaid.ast.MermaidClassMember ret = new io.nop.mermaid.ast.MermaidClassMember();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.visibility != null){
               ret.setVisibility((MermaidClassMember_visibility(ctx.visibility)));
            }
            if(ctx.name != null){
               ret.setName((MermaidClassMember_name(ctx.name)));
            }
            if(ctx.type != null){
               ret.setType((MermaidClassMember_type(ctx.type)));
            }
            if(ctx.isStatic != null){
               ret.setIsStatic((MermaidClassMember_isStatic(ctx.isStatic)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.mermaid.ast.MermaidClassMember> buildMermaidClassMembers_(MermaidClassMembers_Context ctx){
    java.util.List<io.nop.mermaid.ast.MermaidClassMember> list = new ArrayList<>();
    List<MermaidClassMemberContext> elms = ctx.mermaidClassMember();
    if(elms != null){
      for(MermaidClassMemberContext elm: elms){
         list.add(visitMermaidClassMember(elm));
      }
    }
    return list;
}
      
      public io.nop.mermaid.ast.MermaidClassNode visitMermaidClassNode(MermaidClassNodeContext ctx){
          io.nop.mermaid.ast.MermaidClassNode ret = new io.nop.mermaid.ast.MermaidClassNode();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.className != null){
               ret.setClassName((MermaidClassNode_className(ctx.className)));
            }
            if(ctx.members != null){
               ret.setMembers((buildMermaidClassMembers_(ctx.members)));
            }else{
               ret.setMembers(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidComment visitMermaidComment(MermaidCommentContext ctx){
          io.nop.mermaid.ast.MermaidComment ret = new io.nop.mermaid.ast.MermaidComment();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.content != null){
               ret.setContent((MermaidComment_content(ctx.content)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidDirectionStatement visitMermaidDirectionStatement(MermaidDirectionStatementContext ctx){
          io.nop.mermaid.ast.MermaidDirectionStatement ret = new io.nop.mermaid.ast.MermaidDirectionStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.direction != null){
               ret.setDirection((MermaidDirectionStatement_direction(ctx.direction)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidDocument visitMermaidDocument(MermaidDocumentContext ctx){
          io.nop.mermaid.ast.MermaidDocument ret = new io.nop.mermaid.ast.MermaidDocument();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.type != null){
               ret.setType((MermaidDocument_type(ctx.type)));
            }
            if(ctx.statements != null){
               ret.setStatements((buildMermaidStatements_(ctx.statements)));
            }else{
               ret.setStatements(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidFlowEdge visitMermaidFlowEdge(MermaidFlowEdgeContext ctx){
          io.nop.mermaid.ast.MermaidFlowEdge ret = new io.nop.mermaid.ast.MermaidFlowEdge();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.from != null){
               ret.setFrom((MermaidFlowEdge_from(ctx.from)));
            }
            if(ctx.edgeType != null){
               ret.setEdgeType((MermaidFlowEdge_edgeType(ctx.edgeType)));
            }
            if(ctx.label != null){
               ret.setLabel((MermaidFlowEdge_label(ctx.label)));
            }
            if(ctx.to != null){
               ret.setTo((MermaidFlowEdge_to(ctx.to)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidFlowNode visitMermaidFlowNode(MermaidFlowNodeContext ctx){
          io.nop.mermaid.ast.MermaidFlowNode ret = new io.nop.mermaid.ast.MermaidFlowNode();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.id != null){
               ret.setId((MermaidFlowNode_id(ctx.id)));
            }
            if(ctx.text != null){
               ret.setText((MermaidFlowNode_text(ctx.text)));
            }
            if(ctx.shape != null){
               ret.setShape((MermaidFlowNode_shape(ctx.shape)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidFlowSubgraph visitMermaidFlowSubgraph(MermaidFlowSubgraphContext ctx){
          io.nop.mermaid.ast.MermaidFlowSubgraph ret = new io.nop.mermaid.ast.MermaidFlowSubgraph();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.id != null){
               ret.setId((MermaidFlowSubgraph_id(ctx.id)));
            }
            if(ctx.title != null){
               ret.setTitle((MermaidFlowSubgraph_title(ctx.title)));
            }
            if(ctx.statements != null){
               ret.setStatements((buildMermaidStatements_(ctx.statements)));
            }else{
               ret.setStatements(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidGanttTask visitMermaidGanttTask(MermaidGanttTaskContext ctx){
          io.nop.mermaid.ast.MermaidGanttTask ret = new io.nop.mermaid.ast.MermaidGanttTask();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.id != null){
               ret.setId((MermaidGanttTask_id(ctx.id)));
            }
            if(ctx.title != null){
               ret.setTitle((MermaidGanttTask_title(ctx.title)));
            }
            if(ctx.start != null){
               ret.setStart((MermaidGanttTask_start(ctx.start)));
            }
            if(ctx.duration != null){
               ret.setDuration((MermaidGanttTask_duration(ctx.duration)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidParticipant visitMermaidParticipant(MermaidParticipantContext ctx){
          io.nop.mermaid.ast.MermaidParticipant ret = new io.nop.mermaid.ast.MermaidParticipant();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((MermaidParticipant_name(ctx.name)));
            }
            if(ctx.alias != null){
               ret.setAlias((MermaidParticipant_alias(ctx.alias)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidPieItem visitMermaidPieItem(MermaidPieItemContext ctx){
          io.nop.mermaid.ast.MermaidPieItem ret = new io.nop.mermaid.ast.MermaidPieItem();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.label != null){
               ret.setLabel((MermaidPieItem_label(ctx.label)));
            }
            if(ctx.value != null){
               ret.setValue((MermaidPieItem_value(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidSequenceMessage visitMermaidSequenceMessage(MermaidSequenceMessageContext ctx){
          io.nop.mermaid.ast.MermaidSequenceMessage ret = new io.nop.mermaid.ast.MermaidSequenceMessage();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.from != null){
               ret.setFrom((MermaidSequenceMessage_from(ctx.from)));
            }
            if(ctx.edgeType != null){
               ret.setEdgeType((MermaidSequenceMessage_edgeType(ctx.edgeType)));
            }
            if(ctx.message != null){
               ret.setMessage((MermaidSequenceMessage_message(ctx.message)));
            }
            if(ctx.to != null){
               ret.setTo((MermaidSequenceMessage_to(ctx.to)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidStateNode visitMermaidStateNode(MermaidStateNodeContext ctx){
          io.nop.mermaid.ast.MermaidStateNode ret = new io.nop.mermaid.ast.MermaidStateNode();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.id != null){
               ret.setId((MermaidStateNode_id(ctx.id)));
            }
            if(ctx.description != null){
               ret.setDescription((MermaidStateNode_description(ctx.description)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
      public io.nop.mermaid.ast.MermaidStatement visitMermaidStatement(MermaidStatementContext ctx){
        
            return (io.nop.mermaid.ast.MermaidStatement)this.visitChildren(ctx);
          
      }
            
public java.util.List<io.nop.mermaid.ast.MermaidStatement> buildMermaidStatements_(MermaidStatements_Context ctx){
    java.util.List<io.nop.mermaid.ast.MermaidStatement> list = new ArrayList<>();
    List<MermaidStatementContext> elms = ctx.mermaidStatement();
    if(elms != null){
      for(MermaidStatementContext elm: elms){
         list.add(visitMermaidStatement(elm));
      }
    }
    return list;
}
      
      public io.nop.mermaid.ast.MermaidStyleAttribute visitMermaidStyleAttribute(MermaidStyleAttributeContext ctx){
          io.nop.mermaid.ast.MermaidStyleAttribute ret = new io.nop.mermaid.ast.MermaidStyleAttribute();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.name != null){
               ret.setName((MermaidStyleAttribute_name(ctx.name)));
            }
            if(ctx.value != null){
               ret.setValue((MermaidStyleAttribute_value(ctx.value)));
            }
            ret.normalize();
            ret.validate();
          return ret;
      }
            
public java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> buildMermaidStyleAttributes_(MermaidStyleAttributes_Context ctx){
    java.util.List<io.nop.mermaid.ast.MermaidStyleAttribute> list = new ArrayList<>();
    List<MermaidStyleAttributeContext> elms = ctx.mermaidStyleAttribute();
    if(elms != null){
      for(MermaidStyleAttributeContext elm: elms){
         list.add(visitMermaidStyleAttribute(elm));
      }
    }
    return list;
}
      
      public io.nop.mermaid.ast.MermaidStyleStatement visitMermaidStyleStatement(MermaidStyleStatementContext ctx){
          io.nop.mermaid.ast.MermaidStyleStatement ret = new io.nop.mermaid.ast.MermaidStyleStatement();
          ret.setLocation(ParseTreeHelper.loc(ctx));
          
            if(ctx.target != null){
               ret.setTarget((MermaidStyleStatement_target(ctx.target)));
            }
            if(ctx.attributes != null){
               ret.setAttributes((buildMermaidStyleAttributes_(ctx.attributes)));
            }else{
               ret.setAttributes(Collections.emptyList());
            }
            
            ret.normalize();
            ret.validate();
          return ret;
      }
            
  /**
   * rules: mermaidDocument
   */
  public abstract io.nop.mermaid.ast.MermaidDiagramType MermaidDocument_type(ParseTree node);

  /**
   * rules: mermaidDirectionStatement
   */
  public abstract io.nop.mermaid.ast.MermaidDirection MermaidDirectionStatement_direction(ParseTree node);

  /**
   * rules: mermaidFlowEdge
   */
  public abstract io.nop.mermaid.ast.MermaidEdgeType MermaidFlowEdge_edgeType(ParseTree node);

  /**
   * rules: mermaidSequenceMessage
   */
  public abstract io.nop.mermaid.ast.MermaidEdgeType MermaidSequenceMessage_edgeType(ParseTree node);

  /**
   * rules: mermaidFlowNode
   */
  public abstract io.nop.mermaid.ast.MermaidNodeShape MermaidFlowNode_shape(ParseTree node);

  /**
   * rules: mermaidClassMember
   */
  public abstract io.nop.mermaid.ast.MermaidVisibility MermaidClassMember_visibility(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidClassMember
   */
  public abstract java.lang.Boolean MermaidClassMember_isStatic(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidPieItem
   */
  public abstract java.lang.Number MermaidPieItem_value(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidClassMember
   */
  public abstract java.lang.String MermaidClassMember_name(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidClassMember
   */
  public abstract java.lang.String MermaidClassMember_type(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidClassNode
   */
  public abstract java.lang.String MermaidClassNode_className(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidComment
   */
  public abstract java.lang.String MermaidComment_content(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidFlowEdge
   */
  public abstract java.lang.String MermaidFlowEdge_from(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidFlowEdge
   */
  public abstract java.lang.String MermaidFlowEdge_label(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidFlowEdge
   */
  public abstract java.lang.String MermaidFlowEdge_to(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidFlowNode
   */
  public abstract java.lang.String MermaidFlowNode_id(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidFlowNode
   */
  public abstract java.lang.String MermaidFlowNode_text(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidFlowSubgraph
   */
  public abstract java.lang.String MermaidFlowSubgraph_id(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidFlowSubgraph
   */
  public abstract java.lang.String MermaidFlowSubgraph_title(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidGanttTask
   */
  public abstract java.lang.String MermaidGanttTask_duration(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidGanttTask
   */
  public abstract java.lang.String MermaidGanttTask_id(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidGanttTask
   */
  public abstract java.lang.String MermaidGanttTask_start(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidGanttTask
   */
  public abstract java.lang.String MermaidGanttTask_title(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidParticipant
   */
  public abstract java.lang.String MermaidParticipant_alias(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidParticipant
   */
  public abstract java.lang.String MermaidParticipant_name(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidPieItem
   */
  public abstract java.lang.String MermaidPieItem_label(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidSequenceMessage
   */
  public abstract java.lang.String MermaidSequenceMessage_from(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidSequenceMessage
   */
  public abstract java.lang.String MermaidSequenceMessage_message(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidSequenceMessage
   */
  public abstract java.lang.String MermaidSequenceMessage_to(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidStateNode
   */
  public abstract java.lang.String MermaidStateNode_description(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidStateNode
   */
  public abstract java.lang.String MermaidStateNode_id(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidStyleAttribute
   */
  public abstract java.lang.String MermaidStyleAttribute_name(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidStyleAttribute
   */
  public abstract java.lang.String MermaidStyleAttribute_value(org.antlr.v4.runtime.Token token);

  /**
   * rules: mermaidStyleStatement
   */
  public abstract java.lang.String MermaidStyleStatement_target(org.antlr.v4.runtime.Token token);

}
 // resume CPD analysis - CPD-ON
