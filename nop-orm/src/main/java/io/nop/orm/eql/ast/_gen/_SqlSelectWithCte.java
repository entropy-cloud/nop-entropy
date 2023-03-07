//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlSelectWithCte;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlSelectWithCte extends io.nop.orm.eql.ast.SqlDmlStatement {
    
    protected io.nop.orm.eql.ast.SqlSelect select;
    
    protected java.util.List<io.nop.orm.eql.ast.SqlCteStatement> withCtes;
    

    public _SqlSelectWithCte(){
    }

    
    public io.nop.orm.eql.ast.SqlSelect getSelect(){
        return select;
    }

    public void setSelect(io.nop.orm.eql.ast.SqlSelect value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.select = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlCteStatement> getWithCtes(){
        return withCtes;
    }

    public void setWithCtes(java.util.List<io.nop.orm.eql.ast.SqlCteStatement> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.withCtes = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlCteStatement> makeWithCtes(){
        java.util.List<io.nop.orm.eql.ast.SqlCteStatement> list = getWithCtes();
        if(list == null){
            list = new java.util.ArrayList<>();
            setWithCtes(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("select",getSelect());
       
    }


    public SqlSelectWithCte newInstance(){
      return new SqlSelectWithCte();
    }

    @Override
    public SqlSelectWithCte deepClone(){
       SqlSelectWithCte ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(decorators != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlDecorator> copy_decorators = new java.util.ArrayList<>(decorators.size());
                          for(io.nop.orm.eql.ast.SqlDecorator item: decorators){
                              copy_decorators.add(item.deepClone());
                          }
                          ret.setDecorators(copy_decorators);
                      
                }
            
                if(withCtes != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlCteStatement> copy_withCtes = new java.util.ArrayList<>(withCtes.size());
                          for(io.nop.orm.eql.ast.SqlCteStatement item: withCtes){
                              copy_withCtes.add(item.deepClone());
                          }
                          ret.setWithCtes(copy_withCtes);
                      
                }
            
                if(select != null){
                  
                          ret.setSelect(select.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    processor.accept(child);
                }
            }
            if(withCtes != null){
               for(io.nop.orm.eql.ast.SqlCteStatement child: withCtes){
                    processor.accept(child);
                }
            }
            if(select != null)
                processor.accept(select);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(withCtes != null){
               for(io.nop.orm.eql.ast.SqlCteStatement child: withCtes){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(select != null && processor.apply(select) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.decorators != null){
               int index = this.decorators.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlDecorator> list = this.replaceInList(this.decorators,index,newChild);
                   this.setDecorators(list);
                   return true;
               }
            }
            if(this.withCtes != null){
               int index = this.withCtes.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlCteStatement> list = this.replaceInList(this.withCtes,index,newChild);
                   this.setWithCtes(list);
                   return true;
               }
            }
            if(this.select == oldChild){
               this.setSelect((io.nop.orm.eql.ast.SqlSelect)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.decorators != null){
               int index = this.decorators.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlDecorator> list = this.removeInList(this.decorators,index);
                   this.setDecorators(list);
                   return true;
               }
            }
            if(this.withCtes != null){
               int index = this.withCtes.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlCteStatement> list = this.removeInList(this.withCtes,index);
                   this.setWithCtes(list);
                   return true;
               }
            }
            if(this.select == child){
                this.setSelect(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlSelectWithCte other = (SqlSelectWithCte)node;
    
            if(isListEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
            if(isListEquivalent(this.withCtes,other.getWithCtes())){
               return false;
            }
            if(!isNodeEquivalent(this.select,other.getSelect())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlSelectWithCte;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              if(!decorators.isEmpty())
                                json.put("decorators", decorators);
                          
                    }
                
                    if(withCtes != null){
                      
                              if(!withCtes.isEmpty())
                                json.put("withCtes", withCtes);
                          
                    }
                
                    if(select != null){
                      
                              json.put("select", select);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                decorators = io.nop.api.core.util.FreezeHelper.freezeList(decorators,cascade);         
                withCtes = io.nop.api.core.util.FreezeHelper.freezeList(withCtes,cascade);         
                if(select != null)
                    select.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
