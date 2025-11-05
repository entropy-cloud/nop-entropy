package io.nop.pdf.extract;

import io.nop.core.resource.IResource;

import java.awt.image.BufferedImage;
import java.io.File;


/**
 * 附件可能保存在內存中，也可能保存在工作目录中
 */
public interface IResourceImageHandler {
    interface IResourceImageReference {
        BufferedImage getBufferedImage();

        void saveToFile(File file, String imgType);

        void saveToResource(IResource resource, String imageType);
    }

    IResourceImageReference storeImage(File workDir, BufferedImage image);
}