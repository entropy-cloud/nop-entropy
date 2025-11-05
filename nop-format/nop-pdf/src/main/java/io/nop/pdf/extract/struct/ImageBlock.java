package io.nop.pdf.extract.struct;

import io.nop.pdf.extract.IResourceImageHandler;

public class ImageBlock extends Block {
    
    private int width;
    private int height;

    private IResourceImageHandler.IResourceImageReference reference;

    public IResourceImageHandler.IResourceImageReference getReference() {
        return reference;
    }

    public void setReference( IResourceImageHandler.IResourceImageReference reference ) {
        this.reference = reference;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}
