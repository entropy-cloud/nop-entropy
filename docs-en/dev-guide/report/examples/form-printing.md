# Suit

Suit practices are as follows:

1. Inserting images into the report
2. Setting image visibility during printing

![form-printing/form-printing.png](form-printing/form-printing.png)

## Dynamic Image Generation

If the image is not static, it must be dynamically generated based on conditions. This requires configuring a data generation expression.

Right-click on the image and select "View Selected Text" in the "Replace Text" section to generate image data using the `dataExpr` expression. The returned format should be either `byte[]` or `IResource`.

Note: Insert a single row of `-----` to indicate that the following is the expression part.

![form-printing/data-expr.png](form-printing/data-expr.png)

## Loading Images

To load images, you need to write custom code in the application. Here, `myHelper` is just an example object. You can use it in two ways:
1. In the "Expanded" section using `inject` to get the helper object from the bean container.
2. Using the `import` statement to import the external helper class.
