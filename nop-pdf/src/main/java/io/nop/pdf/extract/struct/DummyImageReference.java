package io.nop.pdf.extract.struct;

import io.nop.pdf.extract.IResourceImageHandler;
import io.nop.core.resource.IResource;

import java.awt.image.BufferedImage;
import java.io.File;

public class DummyImageReference implements IResourceImageHandler.IResourceImageReference {


    @Override
    public BufferedImage getBufferedImage() {
        return null;
    }

    @Override
    public void saveToFile(File file, String imgType) {
    }

    @Override
    public void saveToResource(IResource resource, String imageType) {

    }

}
