package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ChartStyleProviderFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ChartStyleProviderFactory.class);

    public static final ChartStyleProviderFactory INSTANCE = new ChartStyleProviderFactory();

    public IChartStyleProvider createStyleProvider(ExcelOfficePackage pkg, IOfficePackagePart drawingPart, String styleId) {

        if (StringHelper.isEmpty(styleId)) {
            LOG.debug("No style ID provided, using default style provider");
            return new DefaultChartStyleProvider();
        }

        ThemeFileParser.ThemeData themeData = loadThemeFiles(pkg, drawingPart, styleId);
        DefaultChartStyleProvider styleProvider = new DefaultChartStyleProvider();

        if (themeData != null && themeData.getColorScheme() != null) {
            applyThemeDataToProvider(styleProvider, themeData);
            LOG.debug("Successfully applied theme data for style ID: {}", styleId);
        } else {
            LOG.debug("No theme data loaded for style ID: {}, using default colors", styleId);
        }

        return styleProvider;

    }

    private ThemeFileParser.ThemeData loadThemeFiles(ExcelOfficePackage pkg, IOfficePackagePart drawingPart, String styleId) {

        String themeBasePath = "xl/theme/";
        String stylesPath = themeBasePath + "style" + styleId + ".xml";
        String colorsPath = themeBasePath + "colors" + styleId + ".xml";

        IOfficePackagePart stylesPart = null;
        IOfficePackagePart colorsPart = null;

        try {
            stylesPart = pkg.getFile(stylesPath);
        } catch (Exception e) {
            LOG.debug("Style file not found at: {}", stylesPath);
        }

        try {
            colorsPart = pkg.getFile(colorsPath);
        } catch (Exception e) {
            LOG.debug("Colors file not found at: {}", colorsPath);
        }

        if (stylesPart == null && colorsPart == null) {
            return loadDefaultThemeFiles(pkg);
        }

        ThemeFileParser.ThemeData themeData = new ThemeFileParser.ThemeData();

        if (stylesPart != null) {
            XNode stylesNode = stylesPart.loadXml();
            if (stylesNode != null) {
                ThemeFileParser.ThemeData stylesThemeData = ThemeFileParser.INSTANCE.parseStylesFile(stylesNode);
                if (stylesThemeData != null) {
                    mergeThemeData(themeData, stylesThemeData);
                    LOG.debug("Successfully loaded styles from: {}", stylesPath);
                }
            }
        }

        if (colorsPart != null) {
            XNode colorsNode = colorsPart.loadXml();
            if (colorsNode != null) {
                ThemeFileParser.ColorScheme colorScheme = ThemeFileParser.INSTANCE.parseColorsFile(colorsNode);
                if (colorScheme != null) {
                    themeData.setColorScheme(colorScheme);
                    LOG.debug("Successfully loaded colors from: {}", colorsPath);
                }
            }
        }

        return themeData;

    }

    private ThemeFileParser.ThemeData loadDefaultThemeFiles(ExcelOfficePackage pkg) {

        String[] defaultThemePaths = {
                "xl/theme/theme1.xml",
                "xl/theme/theme.xml"
        };

        for (String themePath : defaultThemePaths) {
            try {
                IOfficePackagePart themePart = pkg.getFile(themePath);
                if (themePart != null) {
                    XNode themeNode = themePart.loadXml();
                    if (themeNode != null) {
                        ThemeFileParser.ThemeData themeData = ThemeFileParser.INSTANCE.parseStylesFile(themeNode);
                        if (themeData != null) {
                            LOG.debug("Successfully loaded default theme from: {}", themePath);
                            return themeData;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.debug("Default theme file not found at: {}", themePath);
            }
        }

        LOG.debug("No default theme files found");
        return null;

    }

    private void mergeThemeData(ThemeFileParser.ThemeData target, ThemeFileParser.ThemeData source) {
        if (source == null) return;

        if (source.getColorScheme() != null) {
            if (target.getColorScheme() == null) {
                target.setColorScheme(source.getColorScheme());
            } else {
                for (Map.Entry<String, String> entry : source.getColorScheme().getAllColors().entrySet()) {
                    target.getColorScheme().addColor(entry.getKey(), entry.getValue());
                }
            }
        }

        if (source.getMajorFont() != null && target.getMajorFont() == null) {
            target.setMajorFont(source.getMajorFont());
        }
        if (source.getMinorFont() != null && target.getMinorFont() == null) {
            target.setMinorFont(source.getMinorFont());
        }
        if (source.getFormatSchemeName() != null && target.getFormatSchemeName() == null) {
            target.setFormatSchemeName(source.getFormatSchemeName());
        }
    }

    private void applyThemeDataToProvider(DefaultChartStyleProvider styleProvider, ThemeFileParser.ThemeData themeData) {

        if (themeData == null) {
            LOG.debug("No theme data to apply");
            return;
        }

        styleProvider.setThemeData(themeData);

        if (themeData.getColorScheme() != null) {
            Map<String, String> themeColors = themeData.getColorScheme().getAllColors();
            if (!themeColors.isEmpty()) {
                styleProvider.setThemeColors(themeColors);
                LOG.debug("Applied {} theme colors to style provider", themeColors.size());
                logKeyThemeColors(themeColors);
            } else {
                LOG.debug("No theme colors found in color scheme");
            }
        } else {
            LOG.debug("No color scheme found in theme data");
        }

        String majorFont = themeData.getMajorFont();
        String minorFont = themeData.getMinorFont();
        if (majorFont != null || minorFont != null) {
            styleProvider.setThemeFonts(majorFont, minorFont);
            LOG.debug("Applied theme fonts - Major: {}, Minor: {}", majorFont, minorFont);
        }

        if (themeData.getFormatSchemeName() != null) {
            LOG.debug("Theme format scheme: {}", themeData.getFormatSchemeName());
        }

        LOG.debug("Successfully applied theme data to style provider");

    }

    private void logKeyThemeColors(Map<String, String> themeColors) {
        String[] keyColors = {"tx1", "tx2", "bg1", "bg2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6"};

        for (String colorKey : keyColors) {
            String colorValue = themeColors.get(colorKey);
            if (colorValue != null) {
                LOG.debug("Key theme color: {} -> {}", colorKey, colorValue);
            }
        }
    }
}
