package io.nop.pdf.extract.struct;

import java.awt.geom.Rectangle2D;

public class Block {

    /**
     * 唯一编号
     */
  //  private long id;
    
    /**
     * 所在页码
     */
    private int pageNo;
    
    /**
     * 在一页中的块编号。所在页码与pageBlockId共同决定整个文档唯一的块id.
     * 整个页面解析完毕后会调用page.resetBlockIndex重新设置这里的值
     */
    private int pageBlockIndex = -1;
    
    /**
     * 在页面视图中的位置和大小
     */
    private Rectangle2D viewBounding;
    
    //public long getId() {
     //   return id;
    //}
    
    //public void setId( long id ) {
     //   this.id = id;
    //}
    
    public int getPageBlockIndex(){
    	return pageBlockIndex;
    }
    
    public void setPageBlockIndex(int value){
    	this.pageBlockIndex = value;
    }
    
    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo( int pageNo ) {
        this.pageNo = pageNo;
    }
    
    public Rectangle2D getViewBounding() {
        return viewBounding;
    }
    
    public void setViewBounding( Rectangle2D viewBounding ) {
        this.viewBounding = viewBounding;
    }
    
    public void increateViewBounding( Rectangle2D rect ) {
        
        if( this.viewBounding == null ) {
            this.viewBounding = new Rectangle2D.Double( rect.getMinX(), rect.getMinY(), rect.getWidth(), rect.getHeight() );
            return;
        }
        
        this.viewBounding = this.viewBounding.createUnion( rect );
    }
}
