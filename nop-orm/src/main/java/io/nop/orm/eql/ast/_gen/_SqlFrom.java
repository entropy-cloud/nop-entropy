//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlFrom;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlFrom extends EqlASTNode {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlDecorator> decorators;
    
    protected java.util.List<io.nop.orm.eql.ast.SqlTableSource> tableSources;
    

    public _SqlFrom(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlDecorator> getDecorators(){
        return decorators;
    }

    public void setDecorators(java.util.List<io.nop.orm.eql.ast.SqlDecorator> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.decorators = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlDecorator> makeDecorators(){
        java.util.List<io.nop.orm.eql.ast.SqlDecorator> list = getDecorators();
        if(list == null){
            list = new java.util.ArrayList<>();
            setDecorators(list);
        }
        return list;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlTableSource> getTableSources(){
        return tableSources;
    }

    public void setTableSources(java.util.List<io.nop.orm.eql.ast.SqlTableSource> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.tableSources = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlTableSource> makeTableSources(){
        java.util.List<io.nop.orm.eql.ast.SqlTableSource> list = getTableSources();
        if(list == null){
            list = new java.util.ArrayList<>();
            setTableSources(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("tableSources",getTableSources());
       
    }


    public SqlFrom newInstance(){
      return new SqlFrom();
    }

    @Override
    public SqlFrom deepClone(){
       SqlFrom ret = newInstance();
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
            
                if(tableSources != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlTableSource> copy_tableSources = new java.util.ArrayList<>(tableSources.size());
                          for(io.nop.orm.eql.ast.SqlTableSource item: tableSources){
                              copy_tableSources.add(item.deepClone());
                          }
                          ret.setTableSources(copy_tableSources);
                      
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
            if(tableSources != null){
               for(io.nop.orm.eql.ast.SqlTableSource child: tableSources){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(tableSources != null){
               for(io.nop.orm.eql.ast.SqlTableSource child: tableSources){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
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
            if(this.tableSources != null){
               int index = this.tableSources.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlTableSource> list = this.replaceInList(this.tableSources,index,newChild);
                   this.setTableSources(list);
                   return true;
               }
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
            if(this.tableSources != null){
               int index = this.tableSources.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlTableSource> list = this.removeInList(this.tableSources,index);
                   this.setTableSources(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlFrom other = (SqlFrom)node;
    
            if(isListEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
            if(isListEquivalent(this.tableSources,other.getTableSources())){
               return false;
            }
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlFrom;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              if(!decorators.isEmpty())
                                json.put("decorators", decorators);
                          
                    }
                
                    if(tableSources != null){
                      
                              if(!tableSources.isEmpty())
                                json.put("tableSources", tableSources);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                decorators = io.nop.api.core.util.FreezeHelper.freezeList(decorators,cascade);         
                tableSources = io.nop.api.core.util.FreezeHelper.freezeList(tableSources,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
