//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlBinaryExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.ast.SqlExpr left;
    
    protected io.nop.orm.eql.enums.SqlOperator operator;
    
    protected io.nop.orm.eql.ast.SqlExpr right;
    

    public _SqlBinaryExpr(){
    }

    
    public io.nop.orm.eql.ast.SqlExpr getLeft(){
        return left;
    }

    public void setLeft(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.left = value;
    }
    
    public io.nop.orm.eql.enums.SqlOperator getOperator(){
        return operator;
    }

    public void setOperator(io.nop.orm.eql.enums.SqlOperator value){
        checkAllowChange();
        
        this.operator = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getRight(){
        return right;
    }

    public void setRight(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.right = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("left",getLeft());
       
          checkMandatory("operator",getOperator());
       
          checkMandatory("right",getRight());
       
    }


    public SqlBinaryExpr newInstance(){
      return new SqlBinaryExpr();
    }

    @Override
    public SqlBinaryExpr deepClone(){
       SqlBinaryExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(left != null){
                  
                          ret.setLeft(left.deepClone());
                      
                }
            
                if(operator != null){
                  
                          ret.setOperator(operator);
                      
                }
            
                if(right != null){
                  
                          ret.setRight(right.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(left != null)
                processor.accept(left);
        
            if(right != null)
                processor.accept(right);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(left != null && processor.apply(left) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(right != null && processor.apply(right) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.left == oldChild){
               this.setLeft((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.right == oldChild){
               this.setRight((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.left == child){
                this.setLeft(null);
                return true;
            }
        
            if(this.right == child){
                this.setRight(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlBinaryExpr other = (SqlBinaryExpr)node;
    
            if(!isNodeEquivalent(this.left,other.getLeft())){
               return false;
            }
        
                if(!isValueEquivalent(this.operator,other.getOperator())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.right,other.getRight())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlBinaryExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(left != null){
                      
                              json.put("left", left);
                          
                    }
                
                    if(operator != null){
                      
                              json.put("operator", operator);
                          
                    }
                
                    if(right != null){
                      
                              json.put("right", right);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(left != null)
                    left.freeze(cascade);
                if(right != null)
                    right.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
