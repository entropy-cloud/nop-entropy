package io.nop.office.model;

public class OfficeBorder<T extends OfficeBorderStyle> {
    private T bottomBorder;
    private T diagonalLeftBorder;
    private T diagonalRightBorder;
    private T topBorder;
    private T leftBorder;
    private T rightBorder;

    public T getBottomBorder() {
        return bottomBorder;
    }

    public void setBottomBorder(T bottomBorder) {
        this.bottomBorder = bottomBorder;
    }

    public T getDiagonalLeftBorder() {
        return diagonalLeftBorder;
    }

    public void setDiagonalLeftBorder(T diagonalLeftBorder) {
        this.diagonalLeftBorder = diagonalLeftBorder;
    }

    public T getDiagonalRightBorder() {
        return diagonalRightBorder;
    }

    public void setDiagonalRightBorder(T diagonalRightBorder) {
        this.diagonalRightBorder = diagonalRightBorder;
    }

    public T getTopBorder() {
        return topBorder;
    }

    public void setTopBorder(T topBorder) {
        this.topBorder = topBorder;
    }

    public T getLeftBorder() {
        return leftBorder;
    }

    public void setLeftBorder(T leftBorder) {
        this.leftBorder = leftBorder;
    }

    public T getRightBorder() {
        return rightBorder;
    }

    public void setRightBorder(T rightBorder) {
        this.rightBorder = rightBorder;
    }
}
