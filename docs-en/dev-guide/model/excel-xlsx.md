# 1. Parse the Excel file to obtain a Workbook object

```
ExcelWorkbook wk = new ExcelWorkbookParser().parseFromVirtualPath(path);
Or
wk = new ExcelWorkbookParser().parseFromResource(new FileResource(file));
```

## 2. Parse the Excel file to obtain domain objects

Use the import configuration in imp.xml to automatically parse the Excel file into domain model objects.

```
FileResource resource = new FileResource(new File("c:/test.orm.xlsx"));
OrmModel ormModel = (OrmModel) new XlsxObjectLoader("/nop/orm/imp/orm.imp.xml").parseFromResource(resource);
```

## 3. Save the Workbook object to a file

```
new ExcelTemplate(workbook).generateToFile(file, DisabledEvalScope.INSTANCE);
```

## 4. Convert the Workbook to HTML format

```
ITemplateOutput output = reportEngine.getRendererForExcel(workbook, XptConstants.RENDER_TYPE_HTML);
output.generateToFile(file, DisabledEvalScope.INSTANCE);

Or

ITextTemplateOutput output = reportEngine.getHtmlRenderer(workbook);
String html = output.generateText(DisabledEvalScope.INSTANCE);
```

## 5. Save the domain object in XLSX format

Refer to the GenOrmHelper.saveOrmToExcel function

```
IEvalScope scope = XLang.newEvalScope();
scope.setLocalValue(null, XptConstants.VAR_ENTITY, ormModel);

ExcelWorkbook workbook = reportEngine.buildXptModelFromImpModel("/nop/orm/imp/orm.imp.xml");
ITemplateOutput output = reportEngine.getRendererForXptModel(workbook, "xlsx");
output.generateToFile(outputFile, scope);
```

## 6. Parse an ExcelSheet to obtain a domain object

```
ExcelSheet configSheet = workbook.getSheet("Config");
XLangCompileTool compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);

ImportModel importModel = ImportModelHelper.getImportModel(RuleConstants.IMP_PATH_RULE);
ImportSheetModel sheetModel = importModel.getSheet(RuleConstants.SHEET_NAME_CONFIG);
RuleModel rule = ImportModelHelper.parseSheet(sheetModel, configSheet, compileTool, RuleModel.class);
```
<!-- SOURCE_MD5:667170a51e6067fd6c14649f130d6a28-->
