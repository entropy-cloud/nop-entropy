//__XGEN_FORCE_OVERRIDE__
package io.nop.mermaid.ast._gen;

import io.nop.mermaid.ast.MermaidFlowSubgraph;
import io.nop.mermaid.ast.MermaidASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.mermaid.ast.MermaidASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _MermaidFlowSubgraph extends io.nop.mermaid.ast.MermaidStatement {
    
    protected java.lang.String id;
    
    protected java.util.List<io.nop.mermaid.ast.MermaidStatement> statements;
    
    protected java.lang.String title;
    

    public _MermaidFlowSubgraph(){
    }

    
    public java.lang.String getId(){
        return id;
    }

    public void setId(java.lang.String value){
        checkAllowChange();
        
        this.id = value;
    }
    
    public java.util.List<io.nop.mermaid.ast.MermaidStatement> getStatements(){
        return statements;
    }

    public void setStatements(java.util.List<io.nop.mermaid.ast.MermaidStatement> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((MermaidASTNode)this));
                }
            
        this.statements = value;
    }
    
    public java.util.List<io.nop.mermaid.ast.MermaidStatement> makeStatements(){
        java.util.List<io.nop.mermaid.ast.MermaidStatement> list = getStatements();
        if(list == null){
            list = new java.util.ArrayList<>();
            setStatements(list);
        }
        return list;
    }
    
    public java.lang.String getTitle(){
        return title;
    }

    public void setTitle(java.lang.String value){
        checkAllowChange();
        
        this.title = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("id",getId());
       
          checkMandatory("statements",getStatements());
       
    }


    public MermaidFlowSubgraph newInstance(){
      return new MermaidFlowSubgraph();
    }

    @Override
    public MermaidFlowSubgraph deepClone(){
       MermaidFlowSubgraph ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(id != null){
                  
                          ret.setId(id);
                      
                }
            
                if(title != null){
                  
                          ret.setTitle(title);
                      
                }
            
                if(statements != null){
                  
                          java.util.List<io.nop.mermaid.ast.MermaidStatement> copy_statements = new java.util.ArrayList<>(statements.size());
                          for(io.nop.mermaid.ast.MermaidStatement item: statements){
                              copy_statements.add(item.deepClone());
                          }
                          ret.setStatements(copy_statements);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<MermaidASTNode> processor){
    
            if(statements != null){
               for(io.nop.mermaid.ast.MermaidStatement child: statements){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<MermaidASTNode,ProcessResult> processor){
    
            if(statements != null){
               for(io.nop.mermaid.ast.MermaidStatement child: statements){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(MermaidASTNode oldChild, MermaidASTNode newChild){
    
            if(this.statements != null){
               int index = this.statements.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.mermaid.ast.MermaidStatement> list = this.replaceInList(this.statements,index,newChild);
                   this.setStatements(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(MermaidASTNode child){
    
            if(this.statements != null){
               int index = this.statements.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.mermaid.ast.MermaidStatement> list = this.removeInList(this.statements,index);
                   this.setStatements(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(MermaidASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    MermaidFlowSubgraph other = (MermaidFlowSubgraph)node;
    
                if(!isValueEquivalent(this.id,other.getId())){
                   return false;
                }
            
                if(!isValueEquivalent(this.title,other.getTitle())){
                   return false;
                }
            
            if(isListEquivalent(this.statements,other.getStatements())){
               return false;
            }
        return true;
    }

    @Override
    public MermaidASTKind getASTKind(){
       return MermaidASTKind.MermaidFlowSubgraph;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(id != null){
                      
                              json.put("id", id);
                          
                    }
                
                    if(title != null){
                      
                              json.put("title", title);
                          
                    }
                
                    if(statements != null){
                      
                              if(!statements.isEmpty())
                                json.put("statements", statements);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                statements = io.nop.api.core.util.FreezeHelper.freezeList(statements,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
