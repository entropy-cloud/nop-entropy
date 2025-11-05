package io.nop.pdf.extract.parser;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;

import io.nop.pdf.extract.IResourceImageHandler;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

public class MemoryImageHandler implements IResourceImageHandler {
	
	public static MemoryImageHandler INSTANCE = new MemoryImageHandler(); 

	static class ImageReference implements IResourceImageReference {
		final BufferedImage image;

		public ImageReference(BufferedImage image) {
			this.image = image;
		}

		@Override
		public BufferedImage getBufferedImage() {
			return image;
		}

		@Override
		public void saveToFile(File file, String imgType) {
			saveToResource(new FileResource( file),
					imgType);
		}

		@Override
		public void saveToResource(IResource resource, String imgType) {
			File localFile = resource.toFile();
			try {
				if (localFile == null) {
					OutputStream os = null;
					try {
						os = new BufferedOutputStream(
								resource.getOutputStream());
						ImageIO.write(image, imgType, os);
					} finally {
						IoHelper.safeClose(os);
					}
				} else {
					localFile.getParentFile().mkdirs();
					ImageIO.write(image, imgType, localFile);
				}
			} catch (IOException e) {
				throw NopException.wrap(e);
			}
		}
	}

	@Override
	public IResourceImageReference storeImage(File workDir, BufferedImage image) {
		return new ImageReference(image);
	}
}