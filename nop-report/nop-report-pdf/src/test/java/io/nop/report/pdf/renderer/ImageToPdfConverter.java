package io.nop.report.pdf.renderer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.IOException;

public class ImageToPdfConverter {

    public static void main(String[] args) {
        String imagePath = "c:/test/test.png";  // 输入的图片文件路径
        String pdfPath = "c:/test/output.pdf";   // 输出的PDF文件路径

        try {
            insertImageToPdf(imagePath, pdfPath);
            System.out.println("图片成功插入到PDF中!");
        } catch (IOException e) {
            System.err.println("处理过程中出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 将图片插入到新的PDF文件中
     *
     * @param imagePath 图片文件路径
     * @param pdfPath   输出的PDF文件路径
     * @throws IOException
     */
    public static void insertImageToPdf(String imagePath, String pdfPath) throws IOException {
        // 创建一个新的PDF文档
        try (PDDocument document = new PDDocument()) {
            // 创建一个新页面(A4尺寸)
            PDPage page = new PDPage();
            document.addPage(page);

            // 从文件创建图像对象
            PDImageXObject pdImage = PDImageXObject.createFromFile(imagePath, document);


            // 创建内容流并添加图片
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdImage, 0, 0, 100, 100);
            }

            // 保存PDF文档
            document.save(pdfPath);
        }
    }
}