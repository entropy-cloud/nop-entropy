//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlDelete;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlDelete extends io.nop.orm.eql.ast.SqlDmlStatement {
    
    protected io.nop.orm.eql.ast.SqlAlias alias;
    
    protected io.nop.orm.eql.ast.SqlTableName tableName;
    
    protected io.nop.orm.eql.ast.SqlWhere where;
    

    public _SqlDelete(){
    }

    
    public io.nop.orm.eql.ast.SqlAlias getAlias(){
        return alias;
    }

    public void setAlias(io.nop.orm.eql.ast.SqlAlias value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.alias = value;
    }
    
    public io.nop.orm.eql.ast.SqlTableName getTableName(){
        return tableName;
    }

    public void setTableName(io.nop.orm.eql.ast.SqlTableName value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.tableName = value;
    }
    
    public io.nop.orm.eql.ast.SqlWhere getWhere(){
        return where;
    }

    public void setWhere(io.nop.orm.eql.ast.SqlWhere value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.where = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("tableName",getTableName());
       
    }


    public SqlDelete newInstance(){
      return new SqlDelete();
    }

    @Override
    public SqlDelete deepClone(){
       SqlDelete ret = newInstance();
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
            
                if(tableName != null){
                  
                          ret.setTableName(tableName.deepClone());
                      
                }
            
                if(alias != null){
                  
                          ret.setAlias(alias.deepClone());
                      
                }
            
                if(where != null){
                  
                          ret.setWhere(where.deepClone());
                      
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
            if(tableName != null)
                processor.accept(tableName);
        
            if(alias != null)
                processor.accept(alias);
        
            if(where != null)
                processor.accept(where);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(tableName != null && processor.apply(tableName) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(alias != null && processor.apply(alias) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(where != null && processor.apply(where) == ProcessResult.STOP)
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
            if(this.tableName == oldChild){
               this.setTableName((io.nop.orm.eql.ast.SqlTableName)newChild);
               return true;
            }
        
            if(this.alias == oldChild){
               this.setAlias((io.nop.orm.eql.ast.SqlAlias)newChild);
               return true;
            }
        
            if(this.where == oldChild){
               this.setWhere((io.nop.orm.eql.ast.SqlWhere)newChild);
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
            if(this.tableName == child){
                this.setTableName(null);
                return true;
            }
        
            if(this.alias == child){
                this.setAlias(null);
                return true;
            }
        
            if(this.where == child){
                this.setWhere(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlDelete other = (SqlDelete)node;
    
            if(isListEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
            if(!isNodeEquivalent(this.tableName,other.getTableName())){
               return false;
            }
        
            if(!isNodeEquivalent(this.alias,other.getAlias())){
               return false;
            }
        
            if(!isNodeEquivalent(this.where,other.getWhere())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlDelete;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              if(!decorators.isEmpty())
                                json.put("decorators", decorators);
                          
                    }
                
                    if(tableName != null){
                      
                              json.put("tableName", tableName);
                          
                    }
                
                    if(alias != null){
                      
                              json.put("alias", alias);
                          
                    }
                
                    if(where != null){
                      
                              json.put("where", where);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                decorators = io.nop.api.core.util.FreezeHelper.freezeList(decorators,cascade);         
                if(tableName != null)
                    tableName.freeze(cascade);
                if(alias != null)
                    alias.freeze(cascade);
                if(where != null)
                    where.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
