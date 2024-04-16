package io.nop.report.ext;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.model.ExcelImage;
import io.nop.integration.api.qrcode.IQrcodeService;
import io.nop.integration.api.qrcode.QrcodeOptions;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.functions.ReportFunctionProvider;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedSheet;

public class ReportExtFunctions {
    public static ICancellable register() {
        return ReportFunctionProvider.INSTANCE.registerStaticFunctions(ReportExtFunctions.class);
    }

    @Description("根据单元格当前值生成条形码或者二维码")
    @EvalMethod
    public static ExcelImage QRCODE(IEvalScope scope) {
        IXptRuntime xptRt = IXptRuntime.fromScope(scope);
        ExpandedCell cell = xptRt.getCell();
        ExpandedSheet sheet = xptRt.getSheet();

        Object value = cell.getValue();
        if (StringHelper.isEmptyObject(value))
            return null;

        ExcelImage image = cell.makeImage();
        ExcelClientAnchor anchor = image.makeAnchor();
        anchor.setRowDelta(cell.getRowSpan());
        anchor.setColDelta(cell.getColSpan());

        QrcodeOptions options = new QrcodeOptions();
        cell.getModel().readExtProps(ReportExtConstants.QR_NS_PREFIX, true, options);
        options.setContent(StringHelper.toString(value, ""));
        if (options.getWidth() <= 0) {
            int colIndex = cell.getColIndex();
            options.setWidth(sheet.getWidth(colIndex, colIndex + cell.getMergeAcross()));
        }

        if (options.getHeight() <= 0) {
            int rowIndex = cell.getRowIndex();
            options.setHeight(sheet.getHeight(rowIndex, rowIndex + cell.getMergeDown()));
        }
        image.setImgType(options.getImgType());

        IQrcodeService qrcodeService = BeanContainer.instance().getBeanByType(IQrcodeService.class);
        byte[] bytes = qrcodeService.createQrcodeBytes(options);
        image.setImgType(image.getImgType());

        image.setDataBytes(bytes);

        return image;
    }

    @Description("根据单元格当前值生成条形码或者二维码")
    @EvalMethod
    public static ExcelImage QRCODE_IMAGE(IEvalScope scope,
                                          @Name("image") ExcelImage image, @Name("options") QrcodeOptions options) {
        IXptRuntime xptRt = IXptRuntime.fromScope(scope);
        ExpandedSheet sheet = xptRt.getSheet();

        image.calcSize(sheet);

        if (options.getWidth() <= 0) {
            options.setWidth(image.getWidth());
        }
        if (options.getHeight() <= 0) {
            options.setHeight(image.getHeight());
        }

        IQrcodeService qrcodeService = BeanContainer.instance().getBeanByType(IQrcodeService.class);
        byte[] bytes = qrcodeService.createQrcodeBytes(options);
        image.setDataBytes(bytes);
        return image;
    }
}
