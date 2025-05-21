package io.nop.pdf.extract.table;

import io.nop.pdf.extract.struct.ShapeBlock;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DefaultTableDetector implements TableDetector {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTableDetector.class);


    /**
     * 表格线最大尺寸，任何宽或高超过此尺寸的图片，都不认为是表格线
     * 默认值6 = 1.5磅线宽 x 4.0
     */
    private double maxLineShapeSize = 6;

    /**
     * 用于判断彼此相连的表格线是否相连的 最大间距
     */
    private double maxLineDist = 2;

    /**
     * 最小列宽
     */
    private double minColWidth = 20;

    /**
     * 最小行高
     */
    private double minRowHeight = 20;

    /**
     * 扫描表格结构
     *
     * @param shapeBlocks
     * @return
     */
    @Override
    public List<TableBlock> process(List<ShapeBlock> shapeBlocks) {

        List<TableBlock> tables = new ArrayList<TableBlock>();

        //TODO: 有些表格使用了虚线，点划线，需要先将这些虚线合并为实线

        //将所有的图形分成若干个接壤的图形簇
        List<ShapeCluster> clusters = this.createShapeClusters(shapeBlocks);

        //根据图形簇产生表格结构 
        for (int i = 0; i < clusters.size(); i++) {

            ShapeCluster cluster = clusters.get(i);
            TableBlock table = this.detectTable(cluster);
            if (table != null) {
                tables.add(table);
            }
        }

        return tables;
    }

    /**
     * 划分图形簇
     *
     * @param shapeBlocks
     * @return
     */
    private List<ShapeCluster> createShapeClusters(List<ShapeBlock> shapeBlocks) {

        List<ShapeCluster> clusters = new ArrayList<ShapeCluster>();

        for (int i = 0; i < shapeBlocks.size(); i++) {

            ShapeBlock shapeBlock = shapeBlocks.get(i);

            Rectangle2D rc = shapeBlock.getViewBounding();

            //排除大面积的图形
            if (rc.getWidth() > maxLineShapeSize && rc.getHeight() > maxLineShapeSize) {
                LOG.trace("exclude large shape {},{} - {},{}", rc.getX(), rc.getY(), rc.getWidth(), rc.getHeight());
                continue;
            }

            //TODO: 收集小面积的图形，检测虚线

            //加入邻近的簇或者创建新的簇
            List<ShapeCluster> adjacentClusters = findAdjacentClusters(shapeBlock, clusters);
            if (adjacentClusters.size() > 1) {

                clusters.removeAll(adjacentClusters);

                ShapeCluster c = mergeCluster(adjacentClusters);
                c.add(shapeBlock);
                clusters.add(c);
            } else if (adjacentClusters.size() == 1) {

                adjacentClusters.get(0).add(shapeBlock);
            } else {
                ShapeCluster c = new ShapeCluster();
                c.add(shapeBlock);
                clusters.add(c);
            }
        }

        return clusters;
    }

    /**
     * 从clusters从查找所有与shape接壤的
     *
     * @param shape
     * @param clusters
     * @return
     */
    private List<ShapeCluster> findAdjacentClusters(ShapeBlock shape, List<ShapeCluster> clusters) {

        List<ShapeCluster> list = new ArrayList<ShapeCluster>();

        for (int i = 0; i < clusters.size(); i++) {

            ShapeCluster cluster = clusters.get(i);

            boolean adjacent = checkAdjacentToCluster(shape, cluster);
            if (adjacent) {
                list.add(cluster);
            }
        }

        return list;
    }


    /**
     * 检查shape是否与cluster接壤
     *
     * @param shape
     * @param cluster
     * @return
     */
    private boolean checkAdjacentToCluster(ShapeBlock shape, ShapeCluster cluster) {

        //根据最小外框排除
        Rectangle2D br = cluster.getBounding();
        if (br != null && !br.isEmpty()) {

            double dist = calcOutDistRect2Rect(br, shape.getViewBounding());
            if (dist > maxLineDist) {
                return false;
            }
        }

        //逐个图形判断
        for (int i = 0; i < cluster.getBlocks().size(); i++) {

            double dist = calcOutDistRect2Rect(cluster.getBlocks().get(i).getViewBounding(), shape.getViewBounding());

            if (dist < maxLineDist) {
                return true;
            }
        }
        return false;
    }

    /**
     * 合并图形簇
     *
     * @param clusters
     * @return
     */
    private ShapeCluster mergeCluster(List<ShapeCluster> clusters) {

        ShapeCluster c = new ShapeCluster();

        for (int i = 0; i < clusters.size(); i++) {

            c.add(clusters.get(i).getBlocks());
        }
        return c;
    }

    /**
     * 检测表格
     *
     * @param cluster
     * @return
     */
    private TableBlock detectTable(ShapeCluster cluster) {

        List<ShapeBlock> horzShapes = new ArrayList<ShapeBlock>();
        List<ShapeBlock> vertShapes = new ArrayList<ShapeBlock>();

        //分辨纵横线
        double minx = Double.MAX_VALUE;
        double miny = Double.MAX_VALUE;
        double maxx = Double.MIN_VALUE;
        double maxy = Double.MIN_VALUE;

        List<ShapeBlock> blocks = cluster.getBlocks();
        for (int i = 0; i < blocks.size(); i++) {

            ShapeBlock shapeBlock = blocks.get(i);

            Rectangle2D rc = shapeBlock.getViewBounding();

            if (rc.getMinX() < minx) minx = rc.getMinX();
            if (rc.getMinY() < miny) miny = rc.getMinY();
            if (rc.getMaxX() > maxx) maxx = rc.getMaxX();
            if (rc.getMaxY() > maxy) maxy = rc.getMaxY();

            if (rc.getHeight() > 0 && (rc.getWidth() / rc.getHeight()) > 5) {

                horzShapes.add(shapeBlock);
            }

            if (rc.getWidth() > 0 && (rc.getHeight() / rc.getWidth()) > 5) {

                vertShapes.add(shapeBlock);
            }
        }

        Collections.sort(vertShapes, new Comparator<ShapeBlock>() {

            @Override
            public int compare(ShapeBlock o1, ShapeBlock o2) {
                double x1 = o1.getViewBounding().getMinX();
                double x2 = o2.getViewBounding().getMinX();

                if (x1 == x2) return 0;
                return x1 < x2 ? -1 : 1;
            }
        });

        Collections.sort(horzShapes, new Comparator<ShapeBlock>() {

            @Override
            public int compare(ShapeBlock o1, ShapeBlock o2) {
                double y1 = o1.getViewBounding().getMinY();
                double y2 = o2.getViewBounding().getMinY();

                if (y1 == y2) return 0;
                return y1 < y2 ? -1 : 1;
            }
        });

        //合并纵横线
        List<Double> xpoints = mergeVertLines(minx, maxx, vertShapes);
        List<Double> ypoints = mergeHorzLines(miny, maxy, horzShapes);

        //合并单元格
        int rows = ypoints.size() - 1;
        int cols = xpoints.size() - 1;

        //Map<String,TableCellBlock> allCells = new HashMap<String,TableCellBlock>();
        TableBlock table = new TableBlock();

        TableCellBlock[][] cells = new TableCellBlock[rows][cols];
        for (int i = 0; i < rows; i++) {

            for (int j = 0; j < cols; j++) {

                if (cells[i][j] != null) continue;

                TableCellBlock cell = new TableCellBlock(i, j, 1, 1);

                //横向扩展
                int colspan = 1;
                if (true) {
                    double x1 = (xpoints.get(j) + xpoints.get(j + 1)) / 2;
                    double y = (ypoints.get(i) + ypoints.get(i + 1)) / 2;
                    for (int p = j + 1; p < cols; p++) {
                        double x2 = (xpoints.get(p) + xpoints.get(p + 1)) / 2;
                        boolean hasIntersect = this.isIntersectWidthVertShapes(y, x1, x2, vertShapes);
                        if (!hasIntersect) colspan = p - j + 1;
                        else break;
                    }
                }

                //纵向扩展
                int rowspan = 1;
                if (true) {

                    for (int p = i + 1; p < rows; p++) {

                        double y1 = (ypoints.get(i) + ypoints.get(i + 1)) / 2;
                        double y2 = (ypoints.get(p) + ypoints.get(p + 1)) / 2;

                        boolean allpassed = true;
                        for (int q = 0; q < colspan; q++) {

                            double x = (xpoints.get(j + q) + xpoints.get(j + q + 1)) / 2;
                            boolean hasIntersect = isIntersectWidthHorzShapes(x, y1, y2, horzShapes);
                            if (hasIntersect) {
                                allpassed = false;
                                break;
                            }
                        }

                        if (allpassed) rowspan = p - i + 1;
                        else break;
                    }
                }

                cell.setRowPos(i);
                cell.setColPos(j);
                cell.setRowspan(rowspan);
                cell.setColspan(colspan);
                for (int p = 0; p < rowspan; p++) {
                    for (int q = 0; q < colspan; q++) {
                        cells[i + p][j + q] = cell;
                    }
                }
                double x = xpoints.get(j);
                double y = ypoints.get(i);
                double w = xpoints.get(j + colspan) - xpoints.get(j);
                double h = ypoints.get(i + rowspan) - ypoints.get(i);
                cell.setViewBounding(new Rectangle2D.Double(x, y, w, h));

                table.addCell(i, j, cell); //.put( "" + i + "," + j, cell );
            }
        }

        // TableBlock table = new TableBlock();

        //table.setRowCount( ypoints.size() - 1 );
        table.setColCount(xpoints.size() - 1);
        table.setXpoints(xpoints);
        table.setYpoints(ypoints);
        table.setViewBounding(new Rectangle2D.Double(minx, miny, maxx - minx, maxy - miny));
        //table.setCells( allCells );

        return table;
    }

    /**
     * 合并纵向图形(原本就在x方向对齐或者坐标接近的图形被合并到一个x坐标点)
     *
     * @param minx
     * @param maxx
     * @param shapeBlocks
     * @return
     */
    private List<Double> mergeVertLines(double minx, double maxx, List<ShapeBlock> shapeBlocks) {

        List<Double> points = new ArrayList<Double>();

        points.add(minx);

        double last = minx;

        for (int i = 0; i < shapeBlocks.size(); i++) {

            Rectangle2D rc = shapeBlocks.get(i).getViewBounding();

            if (rc.contains(last, rc.getCenterY())) {
                continue;
            }

            double d = calcOutDistVLine2Rect(last, rc);
            if (d > minColWidth) {

                last = rc.getCenterX();
                points.add(last);
            }
        }

        if (maxx - last > minColWidth) {
            points.add(maxx);
        }

        return points;
    }

    /**
     * 合并横向图形(原本就在y方向对齐或者坐标接近的图形被合并到一个y坐标点)
     *
     * @param miny
     * @param maxy
     * @param shapeBlocks
     * @return
     */
    private List<Double> mergeHorzLines(double miny, double maxy, List<ShapeBlock> shapeBlocks) {

        List<Double> points = new ArrayList<Double>();

        points.add(miny);

        double last = miny;

        for (int i = 0; i < shapeBlocks.size(); i++) {

            Rectangle2D rc = shapeBlocks.get(i).getViewBounding();

            if (rc.contains(rc.getCenterX(), last)) {
                continue;
            }

            double d = calcOutDistHLine2Rect(last, rc);
            if (d > minRowHeight) {

                last = rc.getCenterY();
                points.add(last);
            }
        }

        return points;
    }

    /**
     * 计算两个矩形外框的最大间距(相交时按0算)
     *
     * @return
     */
    private double calcOutDistRect2Rect(Rectangle2D rc1, Rectangle2D rc2) {

        double dx = Math.abs(rc2.getCenterX() - rc1.getCenterX()) - rc1.getWidth() / 2 - rc2.getWidth() / 2;
        if (dx < 0) dx = 0;

        double dy = Math.abs(rc2.getCenterY() - rc1.getCenterY()) - rc1.getHeight() / 2 - rc2.getHeight() / 2;
        if (dy < 0) dy = 0;

        return Math.max(dx, dy);
    }

    /**
     * 计算纵线与矩形外框在x方向上的距离(相交时按0算)
     *
     * @param x
     * @param rc
     * @return
     */
    private double calcOutDistVLine2Rect(double x, Rectangle2D rc) {

        if (x < rc.getMinX()) return rc.getMinX() - x;
        if (x > rc.getMaxX()) return x - rc.getMaxX();

        return 0;
    }

    /**
     * 计算横线与矩形外框在y方向上的距离(相交时按0算)
     *
     * @param y
     * @param rc
     * @return
     */
    private double calcOutDistHLine2Rect(double y, Rectangle2D rc) {

        if (y < rc.getMinY()) return rc.getMinY() - y;
        if (y > rc.getMaxY()) return y - rc.getMaxY();

        return 0;
    }

    /**
     * 检查由(x1,y,x2,y)定义的线是否与指定的纵线图形相交
     *
     * @param y
     * @param x1
     * @param x2
     * @param shapes
     * @return
     */
    private boolean isIntersectWidthVertShapes(double y, double x1, double x2, List<ShapeBlock> shapes) {

        for (int i = 0; i < shapes.size(); i++) {

            Rectangle2D rc = shapes.get(i).getViewBounding();

            boolean containsY = rc.getMinY() <= y && y <= rc.getMaxY();
            boolean containsX1 = rc.getMinX() <= x1 && x1 <= rc.getMaxX();
            boolean containsX2 = rc.getMinX() <= x2 && x2 <= rc.getMaxX();
            boolean within = x1 <= rc.getMinX() && rc.getMaxX() <= x2;

            if (containsY && (containsX1 || containsX2 || within)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查由 (x,y1,x,y2)定义的线是否与指定横线图形相交
     *
     * @param x
     * @param y1
     * @param y2
     * @param shapes
     * @return
     */
    private boolean isIntersectWidthHorzShapes(double x, double y1, double y2, List<ShapeBlock> shapes) {

        for (int i = 0; i < shapes.size(); i++) {

            Rectangle2D rc = shapes.get(i).getViewBounding();

            boolean containsX = rc.getMinX() <= x && x <= rc.getMaxX();
            boolean containsY1 = rc.getMinY() <= y1 && y1 <= rc.getMaxY();
            boolean containsY2 = rc.getMinY() <= y2 && y2 <= rc.getMaxY();
            boolean within = y1 <= rc.getMinY() && rc.getMaxY() <= y2;

            if (containsX && (containsY1 || containsY2 || within)) {
                return true;
            }
        }
        return false;
    }
}
