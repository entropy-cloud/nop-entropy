//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlJoinTableSource;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlJoinTableSource extends io.nop.orm.eql.ast.SqlTableSource {
    
    protected io.nop.orm.eql.ast.SqlExpr condition;
    
    protected io.nop.orm.eql.enums.SqlJoinType joinType;
    
    protected io.nop.orm.eql.ast.SqlTableSource left;
    
    protected io.nop.orm.eql.ast.SqlTableSource right;
    

    public _SqlJoinTableSource(){
    }

    
    public io.nop.orm.eql.ast.SqlExpr getCondition(){
        return condition;
    }

    public void setCondition(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.condition = value;
    }
    
    public io.nop.orm.eql.enums.SqlJoinType getJoinType(){
        return joinType;
    }

    public void setJoinType(io.nop.orm.eql.enums.SqlJoinType value){
        checkAllowChange();
        
        this.joinType = value;
    }
    
    public io.nop.orm.eql.ast.SqlTableSource getLeft(){
        return left;
    }

    public void setLeft(io.nop.orm.eql.ast.SqlTableSource value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.left = value;
    }
    
    public io.nop.orm.eql.ast.SqlTableSource getRight(){
        return right;
    }

    public void setRight(io.nop.orm.eql.ast.SqlTableSource value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.right = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("joinType",getJoinType());
       
          checkMandatory("left",getLeft());
       
          checkMandatory("right",getRight());
       
    }


    public SqlJoinTableSource newInstance(){
      return new SqlJoinTableSource();
    }

    @Override
    public SqlJoinTableSource deepClone(){
       SqlJoinTableSource ret = newInstance();
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
            
                if(left != null){
                  
                          ret.setLeft(left.deepClone());
                      
                }
            
                if(joinType != null){
                  
                          ret.setJoinType(joinType);
                      
                }
            
                if(right != null){
                  
                          ret.setRight(right.deepClone());
                      
                }
            
                if(condition != null){
                  
                          ret.setCondition(condition.deepClone());
                      
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
            if(left != null)
                processor.accept(left);
        
            if(right != null)
                processor.accept(right);
        
            if(condition != null)
                processor.accept(condition);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(left != null && processor.apply(left) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(right != null && processor.apply(right) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(condition != null && processor.apply(condition) == ProcessResult.STOP)
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
            if(this.left == oldChild){
               this.setLeft((io.nop.orm.eql.ast.SqlTableSource)newChild);
               return true;
            }
        
            if(this.right == oldChild){
               this.setRight((io.nop.orm.eql.ast.SqlTableSource)newChild);
               return true;
            }
        
            if(this.condition == oldChild){
               this.setCondition((io.nop.orm.eql.ast.SqlExpr)newChild);
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
            if(this.left == child){
                this.setLeft(null);
                return true;
            }
        
            if(this.right == child){
                this.setRight(null);
                return true;
            }
        
            if(this.condition == child){
                this.setCondition(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlJoinTableSource other = (SqlJoinTableSource)node;
    
            if(isListEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
            if(!isNodeEquivalent(this.left,other.getLeft())){
               return false;
            }
        
                if(!isValueEquivalent(this.joinType,other.getJoinType())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.right,other.getRight())){
               return false;
            }
        
            if(!isNodeEquivalent(this.condition,other.getCondition())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlJoinTableSource;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              if(!decorators.isEmpty())
                                json.put("decorators", decorators);
                          
                    }
                
                    if(left != null){
                      
                              json.put("left", left);
                          
                    }
                
                    if(joinType != null){
                      
                              json.put("joinType", joinType);
                          
                    }
                
                    if(right != null){
                      
                              json.put("right", right);
                          
                    }
                
                    if(condition != null){
                      
                              json.put("condition", condition);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                decorators = io.nop.api.core.util.FreezeHelper.freezeList(decorators,cascade);         
                if(left != null)
                    left.freeze(cascade);
                if(right != null)
                    right.freeze(cascade);
                if(condition != null)
                    condition.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
