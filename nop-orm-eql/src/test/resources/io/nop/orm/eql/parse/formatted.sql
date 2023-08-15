SELECT
  a.*,
  COALESCE(sum(rpr.pay_money), 0) AS pay_money,
  MAX(rpr.pay_date) AS pay_date
FROM
  (
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        b.employee_or_company_id AS bear_fee_id,
        NULL AS bear_fee_code,
        b.payee_name AS bear_fee_name,
        a.code AS code,
        a.employee_id AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        b.pay_money AS amount,
        0 AS input_tax,
        0 AS final_amount,
        c.plate_id,
        d.complete_time,
        b.process_bill_list_id AS sub_table_id,
        f.code AS package_code,
        a.currency AS currency,
        NULL AS contract_code,
        a.reason AS remark,
        CAST(a.company_id as varchar) AS company_id,
        CAST(a.department_id as varchar) AS department_id,
        NULL AS main_item_id,
        NULL AS sub_item_id,
        a.borrow_money AS reimbursement_amount
      FROM
        employee_money_manages a 
        LEFT JOIN process_bill_list b ON a.employee_money_manage_id = b.business_id
          AND b.process_type = 6
        LEFT JOIN departments c ON a.department_id = c.department_id
        LEFT JOIN system_process_instances d ON a.employee_money_manage_id = d.business_id
          AND d.process_type = 6
          AND d.delete_flag = 0
        LEFT JOIN reimbursement_package_details e ON d.process_instance_id = e.process_instance_id
        LEFT JOIN reimbursement_packages f ON e.reimbursement_package_id = f.reimbursement_package_id
      WHERE
        a.employee_money_manage_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type = 6
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
        AND a.company_id = ?
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        b.employee_or_company_id,
        b.payee_name,
        a.code,
        a.employee_id,
        d.create_time,
        d.process_status,
        b.pay_money,
        c.plate_id,
        d.complete_time,
        b.process_bill_list_id,
        f.code,
        a.currency,
        a.reason,
        a.company_id,
        a.department_id,
        a.borrow_money
    )
    UNION ALL
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        b.employee_or_company_id AS bear_fee_id,
        NULL AS bear_fee_code,
        b.payee_name AS bear_fee_name,
        a.code AS code,
        a.employee_id AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        b.pay_money AS amount,
        0 AS input_tax,
        0 AS final_amount,
        c.plate_id,
        d.complete_time,
        b.process_bill_list_id AS sub_table_id,
        f.code AS package_code,
        a.currency AS currency,
        NULL AS contract_code,
        a.reason AS remark,
        CAST(a.company_id as varchar) AS company_id,
        CAST(a.department_id as varchar) AS department_id,
        NULL AS main_item_id,
        NULL AS sub_item_id,
        a.pay_amount AS reimbursement_amount
      FROM
        pay_money_manages a 
        LEFT JOIN process_bill_list b ON a.pay_money_manage_id = b.business_id
          AND b.process_type = 7
        LEFT JOIN departments c ON a.department_id = c.department_id
        LEFT JOIN system_process_instances d ON a.pay_money_manage_id = d.business_id
          AND d.process_type = 7
          AND d.delete_flag = 0
        LEFT JOIN reimbursement_package_details e ON d.process_instance_id = e.process_instance_id
        LEFT JOIN reimbursement_packages f ON e.reimbursement_package_id = f.reimbursement_package_id
      WHERE
        a.pay_money_manage_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type = 7
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
        AND a.company_id = ?
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        b.employee_or_company_id,
        b.payee_name,
        a.code,
        a.employee_id,
        d.create_time,
        d.process_status,
        b.pay_money,
        c.plate_id,
        d.complete_time,
        b.process_bill_list_id,
        f.code,
        a.currency,
        a.reason,
        a.company_id,
        a.department_id,
        a.pay_amount
    )
    UNION ALL
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        b.department_id AS bear_fee_id,
        NULL AS bear_fee_code,
        c.department_name AS bear_fee_name,
        a.code AS code,
        a.employee_id AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        b.amount AS amount,
        b.input_tax AS input_tax,
        b.final_amount AS final_amount,
        c.plate_id,
        d.complete_time,
        b.normal_reimbursement_link_department_id AS sub_table_id,
        f.code AS package_code,
        a.currency AS currency,
        NULL AS contract_code,
        a.remark AS remark,
        CAST(a.company_id as varchar) AS company_id,
        CAST(a.department_id as varchar) AS department_id,
        CAST(b.main_item_id as varchar) AS main_item_id,
        CAST(b.sub_item_id as varchar) AS sub_item_id,
        a.amount AS reimbursement_amount
      FROM
        project_normal_reimbursements a 
        LEFT JOIN normal_reimbursement_link_departments b ON a.project_normal_reimbursement_id = b.project_normal_reimbursement_id
        LEFT JOIN departments c ON b.department_id = c.department_id
        LEFT JOIN system_process_instances d ON a.project_normal_reimbursement_id = d.business_id
          AND d.process_type IN (3, 49)
          AND d.delete_flag = 0
        LEFT JOIN reimbursement_package_details e ON d.process_instance_id = e.process_instance_id
        LEFT JOIN reimbursement_packages f ON e.reimbursement_package_id = f.reimbursement_package_id
      WHERE
        a.project_normal_reimbursement_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type IN (3, 49)
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
        AND a.company_id = ?
        AND (b.main_item_id = ?
          OR b.sub_item_id = ?)
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        b.department_id,
        c.department_name,
        a.code,
        a.employee_id,
        d.create_time,
        d.process_status,
        b.amount,
        b.input_tax,
        b.final_amount,
        c.plate_id,
        d.complete_time,
        b.normal_reimbursement_link_department_id,
        f.code,
        a.currency,
        a.remark,
        a.company_id,
        a.department_id,
        b.main_item_id,
        b.sub_item_id,
        a.amount
    )
    UNION ALL
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        b.project_id AS bear_fee_id,
        c.contract_code AS bear_fee_code,
        c.project_name AS bear_fee_name,
        a.code AS code,
        a.employee_id AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        b.amount AS amount,
        b.input_tax AS input_tax,
        b.final_amount AS final_amount,
        c.plate_id,
        d.complete_time,
        b.normal_reimbursement_link_project_id AS sub_table_id,
        f.code AS package_code,
        a.currency AS currency,
        g.contract_code AS contract_code,
        a.remark AS remark,
        CAST(a.company_id as varchar) AS company_id,
        CAST(a.department_id as varchar) AS department_id,
        CAST(b.main_item_id as varchar) AS main_item_id,
        CAST(b.sub_item_id as varchar) AS sub_item_id,
        a.amount AS reimbursement_amount
      FROM
        project_normal_reimbursements a 
        LEFT JOIN normal_reimbursement_link_projects b ON a.project_normal_reimbursement_id = b.project_normal_reimbursement_id
        LEFT JOIN projects c ON b.project_id = c.project_id
        LEFT JOIN system_process_instances d ON a.project_normal_reimbursement_id = d.business_id
          AND d.process_type IN (2, 48)
          AND d.delete_flag = 0
        LEFT JOIN reimbursement_package_details e ON d.process_instance_id = e.process_instance_id
        LEFT JOIN reimbursement_packages f ON e.reimbursement_package_id = f.reimbursement_package_id
        LEFT JOIN contracts g ON c.contract_id = g.contract_id
          AND c.contract_id IS NOT NULL
          AND c.contract_id > 0
      WHERE
        a.project_normal_reimbursement_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type IN (2, 48)
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
        AND a.company_id = ?
        AND (b.main_item_id = ?
          OR b.sub_item_id = ?)
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        b.project_id,
        c.contract_code,
        c.project_name,
        a.code,
        a.employee_id,
        d.create_time,
        d.process_status,
        b.amount,
        b.input_tax,
        b.final_amount,
        c.plate_id,
        d.complete_time,
        b.normal_reimbursement_link_project_id,
        f.code,
        a.currency,
        g.contract_code,
        a.remark,
        a.company_id,
        a.department_id,
        b.main_item_id,
        b.sub_item_id,
        a.amount
    )
    UNION ALL
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        b.plate_id AS bear_fee_id,
        NULL AS bear_fee_code,
        b.cost_bearer AS bear_fee_name,
        a.code AS code,
        a.employee_id AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        b.amount AS amount,
        b.input_tax AS input_tax,
        b.final_amount AS final_amount,
        g.plate_id,
        d.complete_time,
        b.normal_reimbursement_link_main_project_id AS sub_table_id,
        f.code AS package_code,
        a.currency AS currency,
        NULL AS contract_code,
        a.remark AS remark,
        CAST(a.company_id as varchar) AS company_id,
        CAST(a.department_id as varchar) AS department_id,
        CAST(b.main_item_id as varchar) AS main_item_id,
        CAST(b.sub_item_id as varchar) AS sub_item_id,
        a.amount AS reimbursement_amount
      FROM
        project_normal_reimbursements a 
        LEFT JOIN normal_reimbursement_link_main_projects b ON a.project_normal_reimbursement_id = b.project_normal_reimbursement_id
        LEFT JOIN pre_project_reimbursement_numbers c ON b.main_project_id = c.pre_project_reimbursement_number_id
        LEFT JOIN system_process_instances d ON a.project_normal_reimbursement_id = d.business_id
          AND d.process_type = 8
          AND d.delete_flag = 0
        LEFT JOIN reimbursement_package_details e ON d.process_instance_id = e.process_instance_id
        LEFT JOIN reimbursement_packages f ON e.reimbursement_package_id = f.reimbursement_package_id
        LEFT JOIN employees g ON a.employee_id = g.employee_id
      WHERE
        a.project_normal_reimbursement_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type = 8
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
        AND a.company_id = ?
        AND (b.main_item_id = ?
          OR b.sub_item_id = ?)
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        b.plate_id,
        b.cost_bearer,
        a.code,
        a.employee_id,
        d.create_time,
        d.process_status,
        b.amount,
        b.input_tax,
        b.final_amount,
        g.plate_id,
        d.complete_time,
        b.normal_reimbursement_link_main_project_id,
        f.code,
        a.currency,
        a.remark,
        a.company_id,
        a.department_id,
        b.main_item_id,
        b.sub_item_id,
        a.amount
    )
    UNION ALL
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        b.department_id AS bear_fee_id,
        '' AS bear_fee_code,
        c.department_name AS bear_fee_name,
        a.code AS code,
        a.applicant AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        b.amount AS amount,
        b.input_tax AS input_tax,
        b.final_amount AS final_amount,
        c.plate_id,
        d.complete_time,
        b.travel_reimbursement_link_department_id AS sub_table_id,
        f.code AS package_code,
        a.currency AS currency,
        NULL AS contract_code,
        a.remark AS remark,
        CAST(a.company_id as varchar) AS company_id,
        CAST(a.department_id as varchar) AS department_id,
        NULL AS main_item_id,
        NULL AS sub_item_id,
        a.amount AS reimbursement_amount
      FROM
        travel_reimbursements a 
        LEFT JOIN travel_reimbursement_link_departments b ON a.travel_reimbursement_id = b.travel_reimbursement_id
        LEFT JOIN departments c ON b.department_id = c.department_id
        LEFT JOIN system_process_instances d ON a.travel_reimbursement_id = d.business_id
          AND d.process_type = 5
          AND d.delete_flag = 0
        LEFT JOIN reimbursement_package_details e ON d.process_instance_id = e.process_instance_id
        LEFT JOIN reimbursement_packages f ON e.reimbursement_package_id = f.reimbursement_package_id
      WHERE
        a.travel_reimbursement_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type = 5
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
        AND a.company_id = ?
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        b.department_id,
        c.department_name,
        a.code,
        a.applicant,
        d.create_time,
        d.process_status,
        b.amount,
        b.input_tax,
        b.final_amount,
        c.plate_id,
        d.complete_time,
        b.travel_reimbursement_link_department_id,
        f.code,
        a.currency,
        a.remark,
        a.company_id,
        a.department_id,
        a.amount
    )
    UNION ALL
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        b.project_id AS bear_fee_id,
        c.contract_code AS bear_fee_code,
        c.project_name AS bear_fee_name,
        a.code AS code,
        a.applicant AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        b.amount AS amount,
        b.input_tax AS input_tax,
        b.final_amount AS final_amount,
        c.plate_id,
        d.complete_time,
        b.travel_reimbursement_link_project_id AS sub_table_id,
        f.code AS package_code,
        a.currency AS currency,
        g.contract_code AS contract_code,
        a.remark AS remark,
        CAST(a.company_id as varchar) AS company_id,
        CAST(a.department_id as varchar) AS department_id,
        NULL AS main_item_id,
        NULL AS sub_item_id,
        a.amount AS reimbursement_amount
      FROM
        travel_reimbursements a 
        LEFT JOIN travel_reimbursement_link_projects b ON a.travel_reimbursement_id = b.travel_reimbursement_id
        LEFT JOIN projects c ON b.project_id = c.project_id
        LEFT JOIN system_process_instances d ON a.travel_reimbursement_id = d.business_id
          AND d.process_type = 4
          AND d.delete_flag = 0
        LEFT JOIN reimbursement_package_details e ON d.process_instance_id = e.process_instance_id
        LEFT JOIN reimbursement_packages f ON e.reimbursement_package_id = f.reimbursement_package_id
        LEFT JOIN contracts g ON c.contract_id = g.contract_id
          AND c.contract_id IS NOT NULL
          AND c.contract_id > 0
      WHERE
        a.travel_reimbursement_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type = 4
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
        AND a.company_id = ?
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        b.project_id,
        c.contract_code,
        c.project_name,
        a.code,
        a.applicant,
        d.create_time,
        d.process_status,
        b.amount,
        b.input_tax,
        b.final_amount,
        c.plate_id,
        d.complete_time,
        b.travel_reimbursement_link_project_id,
        f.code,
        a.currency,
        g.contract_code,
        a.remark,
        a.company_id,
        a.department_id,
        a.amount
    )
    UNION ALL
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        b.plate_id AS bear_fee_id,
        NULL AS bear_fee_code,
        b.cost_bearer AS bear_fee_name,
        a.code AS code,
        a.applicant AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        b.amount AS amount,
        b.input_tax AS input_tax,
        b.final_amount AS final_amount,
        g.plate_id,
        d.complete_time,
        b.travel_reimbursement_link_main_project_id AS sub_table_id,
        f.code AS package_code,
        a.currency AS currency,
        NULL AS contract_code,
        a.remark AS remark,
        CAST(a.company_id as varchar) AS company_id,
        CAST(a.department_id as varchar) AS department_id,
        NULL AS main_item_id,
        NULL AS sub_item_id,
        a.amount AS reimbursement_amount
      FROM
        travel_reimbursements a 
        LEFT JOIN travel_reimbursement_link_main_projects b ON a.travel_reimbursement_id = b.travel_reimbursement_id
        LEFT JOIN pre_project_reimbursement_numbers c ON b.main_project_id = c.pre_project_reimbursement_number_id
        LEFT JOIN system_process_instances d ON a.travel_reimbursement_id = d.business_id
          AND d.process_type = 9
          AND d.delete_flag = 0
        LEFT JOIN reimbursement_package_details e ON d.process_instance_id = e.process_instance_id
        LEFT JOIN reimbursement_packages f ON e.reimbursement_package_id = f.reimbursement_package_id
        LEFT JOIN employees g ON a.applicant = g.employee_id
      WHERE
        a.travel_reimbursement_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type = 9
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
        AND a.company_id = ?
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        b.plate_id,
        b.cost_bearer,
        a.code,
        a.applicant,
        d.create_time,
        d.process_status,
        b.amount,
        b.input_tax,
        b.final_amount,
        g.plate_id,
        d.complete_time,
        b.travel_reimbursement_link_main_project_id,
        f.code,
        a.currency,
        a.remark,
        a.company_id,
        a.department_id,
        a.amount
    )
    UNION ALL
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        a.id AS bear_fee_id,
        a.code AS bear_fee_code,
        a.name AS bear_fee_name,
        NULL AS code,
        a.drafter AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        NULL AS amount,
        NULL AS input_tax,
        CASE
          WHEN a.cost IS NOT NULL THEN - a.cost
          ELSE 0
        END AS final_amount,
        a.plate_id,
        d.complete_time,
        a.link_id AS sub_table_id,
        NULL AS package_code,
        0 AS currency,
        NULL AS contract_code,
        a.reason AS remark,
        NULL AS company_id,
        NULL AS department_id,
        NULL AS main_item_id,
        NULL AS sub_item_id,
        0 AS reimbursement_amount
      FROM
        reset_costs a 
        LEFT JOIN system_process_instances d ON a.reset_cost_id = d.business_id
          AND d.process_type = 39
          AND d.delete_flag = 0
      WHERE
        a.reset_cost_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type = 39
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        a.id,
        a.code,
        a.name,
        a.drafter,
        d.create_time,
        d.process_status,
        a.plate_id,
        d.complete_time,
        a.link_id,
        a.reason,
        a.cost,
        company_id,
        department_id
    )
    UNION ALL
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        b.id AS bear_fee_id,
        b.code AS bear_fee_code,
        b.name AS bear_fee_name,
        NULL AS code,
        a.drafter AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        NULL AS amount,
        NULL AS input_tax,
        b.cost AS final_amount,
        c.plate_id,
        d.complete_time,
        b.cost_allocation_id AS sub_table_id,
        NULL AS package_code,
        0 AS currency,
        NULL AS contract_code,
        a.reason AS remark,
        NULL AS company_id,
        NULL AS department_id,
        NULL AS main_item_id,
        NULL AS sub_item_id,
        0 AS reimbursement_amount
      FROM
        reset_costs a 
        LEFT JOIN cost_allocations b ON a.reset_cost_id = b.reset_cost_id
          AND b.type IN (3, 4)
        LEFT JOIN departments c ON b.id = c.department_id
        LEFT JOIN system_process_instances d ON a.reset_cost_id = d.business_id
          AND d.process_type = 39
          AND d.delete_flag = 0
      WHERE
        b.type IN (3, 4)
        AND a.reset_cost_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type = 39
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        b.id,
        b.code,
        b.name,
        a.drafter,
        d.create_time,
        d.process_status,
        b.cost,
        c.plate_id,
        d.complete_time,
        b.cost_allocation_id,
        a.reason,
        company_id,
        department_id
    )
    UNION ALL
    (
      SELECT
        d.process_type AS process_type,
        d.business_id AS business_id,
        d.process_id AS process_id,
        d.process_instance_id AS process_instance_id,
        b.id AS bear_fee_id,
        b.code AS bear_fee_code,
        b.name AS bear_fee_name,
        NULL AS code,
        a.drafter AS employee_id,
        d.create_time AS create_time,
        d.process_status AS process_status,
        NULL AS amount,
        NULL AS input_tax,
        b.cost AS final_amount,
        c.plate_id,
        d.complete_time,
        b.cost_allocation_id AS sub_table_id,
        NULL AS package_code,
        0 AS currency,
        NULL AS contract_code,
        a.reason AS remark,
        NULL AS company_id,
        NULL AS department_id,
        NULL AS main_item_id,
        NULL AS sub_item_id,
        0 AS reimbursement_amount
      FROM
        reset_costs a 
        LEFT JOIN cost_allocations b ON a.reset_cost_id = b.reset_cost_id
          AND b.type = 1
        LEFT JOIN projects c ON b.id = c.project_id
        LEFT JOIN system_process_instances d ON a.reset_cost_id = d.business_id
          AND d.process_type = 39
          AND d.delete_flag = 0
        LEFT JOIN project_cost e ON d.process_instance_id = e.process_instance_id
          AND b.id = e.project_id
      WHERE
        b.type = 1
        AND a.reset_cost_id IN 
          (
            SELECT
              business_id
            FROM
              system_process_instances
            WHERE
              delete_flag = 0
              AND process_type = 39
              AND create_time >= ?
              AND create_time < ?
              AND complete_time >= ?
              AND complete_time < ?
          )
      GROUP BY
        d.process_type,
        d.business_id,
        d.process_id,
        d.process_instance_id,
        b.id,
        b.code,
        b.name,
        a.drafter,
        d.create_time,
        d.process_status,
        b.cost,
        c.plate_id,
        d.complete_time,
        b.cost_allocation_id,
        a.reason,
        company_id,
        department_id
    )
  )  AS a 
  LEFT JOIN reimbursement_payment_records rpr ON a.process_type = rpr.process_type
    AND a.business_id = rpr.business_id
WHERE
  a.process_type = ?
  AND a.bear_fee_code LIKE ?
  AND a.bear_fee_name LIKE ?
  AND a.code LIKE ?
  AND a.employee_id = ?
  AND a.plate_id = ?
  AND a.process_status = ?
  AND a.package_code LIKE ?
  AND a.currency = ?
  AND a.contract_code LIKE ?
  AND a.remark LIKE ?
  AND (cast (a.main_item_id as integer) = ?
    OR cast (a.sub_item_id as integer) = ?)
GROUP BY
  a.process_type,
  a.business_id,
  a.process_id,
  a.process_instance_id,
  a.bear_fee_id,
  a.bear_fee_code,
  a.bear_fee_name,
  a.code,
  a.employee_id,
  a.create_time,
  a.process_status,
  a.amount,
  a.input_tax,
  a.final_amount,
  a.plate_id,
  a.complete_time,
  a.sub_table_id,
  a.package_code,
  a.currency,
  a.contract_code,
  a.remark,
  a.company_id,
  a.department_id,
  a.main_item_id,
  a.sub_item_id,
  a.reimbursement_amount
ORDER BY
  a.code,
  a.process_type,
  a.business_id,
  a.final_amount

