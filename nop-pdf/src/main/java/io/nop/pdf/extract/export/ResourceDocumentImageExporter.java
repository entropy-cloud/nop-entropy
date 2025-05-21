package io.nop.pdf.extract.export;

import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ResourceDocumentImageExporter extends AbstractResourceDocumentExporter {
    static final Logger LOG = LoggerFactory.getLogger(ResourceDocumentImageExporter.class);

    @Override
    public void exportToResource(ResourceDocument doc, IResource resource, String encoding) {

        for (ResourcePage page : doc.getPages()) {

            List<ImageBlock> images = page.getImageBlocks();
            if (images == null) continue;

            for (ImageBlock image : images) {
                if (image.getReference() == null)
                    continue;

                String fileName = "../resources/b" + image.getPageNo() + "-" + image.getPageBlockIndex() + ".jpg";
                IResource imgRes = ResourceHelper.resolveRelativeResource(resource, fileName, true);
                LOG.debug("pdf.export_image:{}", resource.getPath() + "/" + fileName);

                image.getReference().saveToResource(imgRes, "jpg");
            }
        }
    }
}