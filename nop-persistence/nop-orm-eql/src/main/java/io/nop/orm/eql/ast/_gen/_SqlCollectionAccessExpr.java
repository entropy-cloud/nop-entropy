//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlCollectionAccessExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlCollectionAccessExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlExpr> collFuncArgs;
    
    protected java.lang.String collFuncName;
    
    protected io.nop.orm.eql.ast.SqlColumnName collection;
    
    protected io.nop.orm.eql.ast.SqlOrderBy orderBy;
    
    protected io.nop.orm.eql.ast.SqlExpr where;
    

    public _SqlCollectionAccessExpr(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlExpr> getCollFuncArgs(){
        return collFuncArgs;
    }

    public void setCollFuncArgs(java.util.List<io.nop.orm.eql.ast.SqlExpr> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.collFuncArgs = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlExpr> makeCollFuncArgs(){
        java.util.List<io.nop.orm.eql.ast.SqlExpr> list = getCollFuncArgs();
        if(list == null){
            list = new java.util.ArrayList<>();
            setCollFuncArgs(list);
        }
        return list;
    }
    
    public java.lang.String getCollFuncName(){
        return collFuncName;
    }

    public void setCollFuncName(java.lang.String value){
        checkAllowChange();
        
        this.collFuncName = value;
    }
    
    public io.nop.orm.eql.ast.SqlColumnName getCollection(){
        return collection;
    }

    public void setCollection(io.nop.orm.eql.ast.SqlColumnName value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.collection = value;
    }
    
    public io.nop.orm.eql.ast.SqlOrderBy getOrderBy(){
        return orderBy;
    }

    public void setOrderBy(io.nop.orm.eql.ast.SqlOrderBy value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.orderBy = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getWhere(){
        return where;
    }

    public void setWhere(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.where = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("collection",getCollection());
       
    }


    public SqlCollectionAccessExpr newInstance(){
      return new SqlCollectionAccessExpr();
    }

    @Override
    public SqlCollectionAccessExpr deepClone(){
       SqlCollectionAccessExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(collection != null){
                  
                          ret.setCollection(collection.deepClone());
                      
                }
            
                if(where != null){
                  
                          ret.setWhere(where.deepClone());
                      
                }
            
                if(orderBy != null){
                  
                          ret.setOrderBy(orderBy.deepClone());
                      
                }
            
                if(collFuncName != null){
                  
                          ret.setCollFuncName(collFuncName);
                      
                }
            
                if(collFuncArgs != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlExpr> copy_collFuncArgs = new java.util.ArrayList<>(collFuncArgs.size());
                          for(io.nop.orm.eql.ast.SqlExpr item: collFuncArgs){
                              copy_collFuncArgs.add(item.deepClone());
                          }
                          ret.setCollFuncArgs(copy_collFuncArgs);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(collection != null)
                processor.accept(collection);
        
            if(where != null)
                processor.accept(where);
        
            if(orderBy != null)
                processor.accept(orderBy);
        
            if(collFuncArgs != null){
               for(io.nop.orm.eql.ast.SqlExpr child: collFuncArgs){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(collection != null && processor.apply(collection) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(where != null && processor.apply(where) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(orderBy != null && processor.apply(orderBy) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(collFuncArgs != null){
               for(io.nop.orm.eql.ast.SqlExpr child: collFuncArgs){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.collection == oldChild){
               this.setCollection((io.nop.orm.eql.ast.SqlColumnName)newChild);
               return true;
            }
        
            if(this.where == oldChild){
               this.setWhere((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.orderBy == oldChild){
               this.setOrderBy((io.nop.orm.eql.ast.SqlOrderBy)newChild);
               return true;
            }
        
            if(this.collFuncArgs != null){
               int index = this.collFuncArgs.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlExpr> list = this.replaceInList(this.collFuncArgs,index,newChild);
                   this.setCollFuncArgs(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.collection == child){
                this.setCollection(null);
                return true;
            }
        
            if(this.where == child){
                this.setWhere(null);
                return true;
            }
        
            if(this.orderBy == child){
                this.setOrderBy(null);
                return true;
            }
        
            if(this.collFuncArgs != null){
               int index = this.collFuncArgs.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlExpr> list = this.removeInList(this.collFuncArgs,index);
                   this.setCollFuncArgs(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlCollectionAccessExpr other = (SqlCollectionAccessExpr)node;
    
            if(!isNodeEquivalent(this.collection,other.getCollection())){
               return false;
            }
        
            if(!isNodeEquivalent(this.where,other.getWhere())){
               return false;
            }
        
            if(!isNodeEquivalent(this.orderBy,other.getOrderBy())){
               return false;
            }
        
                if(!isValueEquivalent(this.collFuncName,other.getCollFuncName())){
                   return false;
                }
            
            if(isListEquivalent(this.collFuncArgs,other.getCollFuncArgs())){
               return false;
            }
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlCollectionAccessExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(collection != null){
                      
                              json.put("collection", collection);
                          
                    }
                
                    if(where != null){
                      
                              json.put("where", where);
                          
                    }
                
                    if(orderBy != null){
                      
                              json.put("orderBy", orderBy);
                          
                    }
                
                    if(collFuncName != null){
                      
                              json.put("collFuncName", collFuncName);
                          
                    }
                
                    if(collFuncArgs != null){
                      
                              if(!collFuncArgs.isEmpty())
                                json.put("collFuncArgs", collFuncArgs);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(collection != null)
                    collection.freeze(cascade);
                if(where != null)
                    where.freeze(cascade);
                if(orderBy != null)
                    orderBy.freeze(cascade);
                collFuncArgs = io.nop.api.core.util.FreezeHelper.freezeList(collFuncArgs,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
