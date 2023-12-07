//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlSubqueryTableSource;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlSubqueryTableSource extends io.nop.orm.eql.ast.SqlTableSource {
    
    protected io.nop.orm.eql.ast.SqlAlias alias;
    
    protected boolean lateral;
    
    protected io.nop.orm.eql.ast.SqlSelect query;
    

    public _SqlSubqueryTableSource(){
    }

    
    public io.nop.orm.eql.ast.SqlAlias getAlias(){
        return alias;
    }

    public void setAlias(io.nop.orm.eql.ast.SqlAlias value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.alias = value;
    }
    
    public boolean getLateral(){
        return lateral;
    }

    public void setLateral(boolean value){
        checkAllowChange();
        
        this.lateral = value;
    }
    
    public io.nop.orm.eql.ast.SqlSelect getQuery(){
        return query;
    }

    public void setQuery(io.nop.orm.eql.ast.SqlSelect value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.query = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("query",getQuery());
       
    }


    public SqlSubqueryTableSource newInstance(){
      return new SqlSubqueryTableSource();
    }

    @Override
    public SqlSubqueryTableSource deepClone(){
       SqlSubqueryTableSource ret = newInstance();
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
            
                ret.setLateral(lateral);
            
                if(query != null){
                  
                          ret.setQuery(query.deepClone());
                      
                }
            
                if(alias != null){
                  
                          ret.setAlias(alias.deepClone());
                      
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
            if(query != null)
                processor.accept(query);
        
            if(alias != null)
                processor.accept(alias);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(query != null && processor.apply(query) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(alias != null && processor.apply(alias) == ProcessResult.STOP)
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
            if(this.query == oldChild){
               this.setQuery((io.nop.orm.eql.ast.SqlSelect)newChild);
               return true;
            }
        
            if(this.alias == oldChild){
               this.setAlias((io.nop.orm.eql.ast.SqlAlias)newChild);
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
            if(this.query == child){
                this.setQuery(null);
                return true;
            }
        
            if(this.alias == child){
                this.setAlias(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlSubqueryTableSource other = (SqlSubqueryTableSource)node;
    
            if(isListEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
                if(!isValueEquivalent(this.lateral,other.getLateral())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.query,other.getQuery())){
               return false;
            }
        
            if(!isNodeEquivalent(this.alias,other.getAlias())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlSubqueryTableSource;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              if(!decorators.isEmpty())
                                json.put("decorators", decorators);
                          
                    }
                
                   json.put("lateral", lateral);
                
                    if(query != null){
                      
                              json.put("query", query);
                          
                    }
                
                    if(alias != null){
                      
                              json.put("alias", alias);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                decorators = io.nop.api.core.util.FreezeHelper.freezeList(decorators,cascade);         
                if(query != null)
                    query.freeze(cascade);
                if(alias != null)
                    alias.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
