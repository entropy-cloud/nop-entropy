
The company has open-sourced a [front-end BI system](http://www.raqsoft.com.cn/r/os-bi), which introduces a unique DQL (Dimensional Query Language) at the technical level.

For detailed information, please refer to the article on the "Yan Academy" website:

- [Farewell to Wide Tables: Empowering BI with DQL - Yan Academy](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0)

RaQSoft's Perspective:
- Users find it difficult to understand complex SQL JOIN operations.
- For multi-dimensional analysis, only wide tables can be used, which pose significant challenges for data preparation.
- DQL simplifies the mental model for users by streamlining JOIN operations while offering superior performance compared to SQL.

Example: Finding **American Managers in China**

```sql
-- SQL
SELECT A.*
FROM Employee A
JOIN Department ON A.Department = Department.ID
JOIN Employee C ON Department.Manager = C.ID
WHERE A.Nationality = 'America' AND C.Nationality = 'China'
```

```sql
-- DQL
SELECT *
FROM Employee
WHERE Nationality = 'America' AND Department.Manager = C.ID
    AND C.Nationality = 'China'
```

Key Points:
- The term "dimensionalization" refers to the process of transforming wide tables into a more flexible format.
- DQL allows users to directly access and manipulate dimensional data without complex joins.

Another Example: Querying Order Data

```sql
-- SQL
SELECT send_city.name city,
       send_city.pid.name province,
       send_city.pid.name region
FROM orders
```

```sql
-- DQL
SELECT send_city.name city,
       send_city.pid.name province,
       send_city.pid.name region
FROM orders
```

Second Key Idea:
- **Dimensionalization**: A one-to-one relationship between tables allows for implicit joins, eliminating the need to explicitly define join conditions.

Third Key Idea:
- **Aggregation of Sub-tables**: For example, an order details table can be treated as a sub-dimension of the orders table.
  
  ```sql
  -- SQL
  SELECT T1.OrderID,
         T1.EmployeeID,
         SUM(T2.Amount) total_amount
  FROM Orders T1
  JOIN OrderDetails T2 ON T1.OrderID = T2.OrderID
  GROUP BY T1.OrderID, T1.EmployeeID
  ```
  
  ```sql
  -- DQL
  SELECT OrderID,
         EmployeeID,
         SUM(Amount) total_amount
  FROM Orders
  ```

Fourth Key Idea:
- **Multi-dimensional Aggregation**: For tables like contracts, invoices, and inventory, DQL simplifies querying by allowing direct aggregation without complex joins.

Final Example: Multi-Table Queries

```sql
-- SQL
SELECT T1.Date,
       T1.Amount,
       T2.Amount,
       T3.Amount
FROM Contracts T1
LEFT JOIN Invoices T2 ON T1.ContractID = T2.ContractID
LEFT JOIN Inventory T3 ON T2.InvoiceID = T3.InvoiceID
```

```sql
-- DQL
SELECT ContractID,
       Amount,
       InvoiceID,
       SUM(Amount) total_amount
FROM Contracts
LEFT JOIN Invoices ON ContractID = InvoiceID
LEFT JOIN Inventory ON InvoiceID = InvoiceID
```

## Understanding DQL and ORM Design from NopOrm's Perspective

### 1. DQL (Data Query Language) Overview
From the perspective of **NopOrm**, it is clear that **DQL** is essentially another form of **ORM (Object-Relational Mapping)** design. 

#### Key Points:
- **DQL** requires the use of a configuration tool (e.g., ORM designer) to define primary and foreign keys, as well as field names explicitly.
- This approach is identical to how **ORM models** are typically designed.

### 2. Differences Between DQL and EAV Model
- **DQL's foreign key association** and **dimension alignment** closely resemble the **EAV (Entity-Attribute-Value)** model's object attribute association, except that DQL directly uses database fields for association names.
- While this approach is straightforward, it struggles with complex primary key associations.

### 3. Dimension Alignment Implementation
- **Dimension alignment** is an interesting concept in itself. Its implementation involves multiple SQL statements to load data and a **Hash Join** in memory for efficient alignment.
- In the context of **paginated queries**, loading only the main table's data and applying pagination on it can significantly speed up operations.

## MdxQueryExecutor: Dimension Alignment Query Executor
The **MdxQueryExecutor** in Nop platform implements a similar concept to DQL's dimension alignment query. Since EAV is already natively supported for object attribute association, all that's needed is the implementation of **QueryBean**'s splitting, sharding, and data fusion.

## MdxQueryExecutor Execution Logic

### 1. Splitting Fields
- Separate out the main table fields and their associated child table fields.

### 2. Automatic Grouping Based on Aggregation Requirements
- If a main table field requires aggregation (e.g., sum), non-aggregated fields are automatically added to the **GROUP BY** clause, becoming part of the **dimFields**.
- When querying child tables, use these **dimFields** for filtering and ensure only relevant records are fetched.

### 3. Intersection of Dimensions
- Retrieve both the main table's **dimFields** and the associated fields from the child table to form their intersection.
- This is necessary because foreign key associations in the main table may have been aggregated, thus potentially losing some **dimFields**.

### 4. One-to-Many Association Handling
- For one-to-many relationships, all **dimFields** must be included in the SELECT clause since they are required for accurate querying and filtering.

### 5. Full Collection Loading
- If it's a one-to-many association, load all related records at once to avoid multiple database calls.

