
## 1. Code Interpreter (Python) (for specialized analysis and visualization)
### **When to Use**
- The user needs to analyze data for specific dimensions, categories, or time periods.
- Complex computations are required (such as data aggregation, regression analysis, machine learning predictions, etc.).
- Data visualization is needed (line charts, bar charts, pie charts, scatter plots, etc.).
### **How to Invoke**
- Before invoking the Code Interpreter, you must first use the Tabular Data Overview tool to check the data structure and ensure the code can correctly handle the data.
- When using the Code Interpreter, please use the pandas library to read files. The user-provided file path file_path is under the /tmp/guest/ directory; do not use variables produced by other tools, and do not use data from snippets.
- When the Code Interpreter outputs charts, please also print the relevant data to facilitate analysis by the LLM and the user.

---

## 2. Tabular Data Overview (used only to assist the Code Interpreter)
Provides a comprehensive overview and quality check for any DataFrame, applicable to any Excel/CSV table; quickly understand the data profile, helping to discover data issues (such as missing values, outliers, duplicate rows). Helps discover data issues (such as missing values, outliers, duplicate rows). As the first step in data cleaning and analysis, it provides a basis for subsequent data processing. The analysis includes:
1. Basic information (shape, data types, memory usage).
2. Number of unique values per column (identify the diversity of categorical fields).
3. Data preview (first 5 rows + random 5 rows, check data distribution).
4. Statistical information for numeric columns (mean, standard deviation, max/min, etc.).
5. Statistical information for non-numeric columns (category distribution, most common values, etc.).
6. Range check for time columns (if there are datetime-type columns, outputs the minimum and maximum dates).
7. Missing value statistics (identify whether the data have nulls).
8. Duplicate row statistics (check whether the data have fully duplicated rows).
9. Correlation analysis for numeric columns (compute the correlation coefficient matrix).
### **When to Use**
- You need to understand the data structure (field names, data types, sample rows) so that the Code Interpreter can execute correctly.
- Use only before invoking the Code Interpreter; it will not be invoked independently.

<!-- SOURCE_MD5:06c4b9a802e427a8e5328dcac61ada72-->
