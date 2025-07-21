package io.nop.image.utils;

import io.nop.api.core.beans.BinaryDataBean;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class ImageCompressHelper {

    public static BufferedImage readImage(IResource resource) throws IOException {
        File file = resource.toFile();
        if (file != null)
            return ImageIO.read(file);

        InputStream is = resource.getInputStream();
        try {
            return ImageIO.read(is);
        } finally {
            IoHelper.safeCloseObject(is);
        }
    }

    public static BinaryDataBean compressImageWithLimit(IResource inputFile, int maxSize) throws IOException {
        return compressImageWithLimit(inputFile, maxSize, null, null);
    }

    /**
     * 自动识别图片格式，将图片压缩至maxSize(字节)以内，返回byte[]。
     * 若超限，统一转为jpg逐步压缩。
     *
     * @param inputFile 源图片
     * @param maxSize   最大输出字节数
     * @return 满足条件的图片byte[]
     * @throws IOException
     */
    public static BinaryDataBean compressImageWithLimit(
            IResource inputFile,
            int maxSize,
            Integer targetWidth,
            Integer targetHeight
    ) throws IOException {
        // 自动识别格式
        String formatName = getFormatName(inputFile);
        String contentType = ImageTypeMap.INSTANCE.getMimeTypeByFormatName(formatName);

        // 先判断文件本身够不够小
        if (inputFile.length() <= maxSize) {
            return new BinaryDataBean(contentType, ResourceHelper.readBytes(inputFile));
        }

        // 读取原始图片
        BufferedImage image = readImage(inputFile);
        if (image == null) {
            throw new IOException("unable to read file:" + inputFile.getPath());
        }

        // 尝试原尺寸原格式
        byte[] originalBytes = bufferedImageToBytes(image, formatName, 1.0f);
        if (originalBytes.length <= maxSize) {
            return new BinaryDataBean(contentType, originalBytes);
        }

        // 计算目标宽高，保持宽高比
        int srcWidth = image.getWidth();
        int srcHeight = image.getHeight();
        int dstWidth = srcWidth, dstHeight = srcHeight;

        if (targetWidth != null && targetHeight == null) {
            dstWidth = targetWidth;
            dstHeight = (int) Math.round((double) srcHeight * targetWidth / srcWidth);
        } else if (targetHeight != null && targetWidth == null) {
            dstHeight = targetHeight;
            dstWidth = (int) Math.round((double) srcWidth * targetHeight / srcHeight);
        } else if (targetWidth != null && targetHeight != null) {
            // 若都给，则按指定宽高（可能破坏比例），也可设为仅用其一，这里按业务决定
            dstWidth = targetWidth;
            dstHeight = targetHeight;
            // tip: 你如需保持比例，只用一个即可
        }

        float quality = 0.92f;
        int minWidth = 100, minHeight = 100;

        while (quality >= 0.3f) {
            int tryWidth = dstWidth, tryHeight = dstHeight;

            while (tryWidth > minWidth && tryHeight > minHeight) {
                BufferedImage resized = resizeImage(image, tryWidth, tryHeight);
                byte[] jpegBytes = bufferedImageToBytes(resized, "jpg", quality);
                if (jpegBytes.length <= maxSize) {
                    return new BinaryDataBean("image/jpeg", jpegBytes);
                }
                // 按比例同步缩小
                tryWidth = Math.max((int) (tryWidth * 0.9), minWidth);
                tryHeight = Math.max((int) (tryHeight * 0.9), minHeight);
            }
            quality -= 0.1f;
        }

        throw new IOException("unable compress image to " + maxSize + " bytes");
    }

    // 工具方法示例
    private static BufferedImage resizeImage(BufferedImage src, int w, int h) {
        Image tmp = src.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    // 获取源图片的格式
    private static String getFormatName(IResource resource) {
        String format = ImageTypeMap.INSTANCE.getFormatNameByFileName(resource.getName());
        if (format != null)
            return format;
        return "png"; // 默认
    }

    // 转为字节数组，支持质量参数（JPEG用）
    private static byte[] bufferedImageToBytes(BufferedImage image, String format, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("未找到JPG写入器");
            }
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(baos);
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
            output.close();
        } else {
            ImageIO.write(image, format, baos);
        }
        return baos.toByteArray();
    }
}