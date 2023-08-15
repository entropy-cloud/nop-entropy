SELECT
  DISTINCT ct.contract_id,
  ct.contract_code,
  ct.contract_name,
  ct.customer_name,
  ct.start_date,
  ct.end_date,
  ct.status,
  ct.project_code,
  ct.contract_type,
  ct.project_level,
  ct.total_project_time,
  ct.signing_money_sum,
  ct.design_areas,
  ct.contract_price,
  ct.project_address,
  ct.contract_follower,
  ct.remark,
  ct.contract_date,
  ct.signing_company_name,
  ct.create_time,
  ct.update_time,
  ct.business_type,
  ct.flow_status,
  ct.serial_number,
  ct.project_type,
  ct.covered_area,
  ct.professional_cooperation,
  ct.province,
  ct.city,
  ct.detail_address,
  ct.customer_address,
  ct.service_suggest_book,
  ct.project_approval_time,
  ct.order_no,
  ct.parent_id,
  ct.info_code,
  ct.contract_status,
  ct.client_contract_id,
  ct.is_finish,
  ct.is_system_add,
  ct.operator,
  ct.attachment_remark,
  ct.contract_info_audited,
  cp.borrow_money,
  amm.count,
  aa.amount AS invoice_amount,
  bb.amount AS project_amount,
  am1.amount AS check_amount,
  cpbm.borrow_money AS current_borrow_money,
  inac.amount AS current_amount,
  cpc.capital_date,
  pjc.settlement_date,
  iv.invoice_date,
  (
    SELECT
      sum(am.amount)
    FROM
      attachment_manages am 
    WHERE
      am.contract_id = ct.contract_id
      AND am.delete_flag = 0
  )  AS amount,
  ct.not_contain_factoring_amount
FROM
  contracts ct 
  LEFT JOIN (
    SELECT
      iv.contract_id,
      MAX(invoice_date) AS invoice_date
    FROM
      invoices iv 
    GROUP BY
      iv.contract_id
  )  AS iv ON iv.contract_id = ct.contract_id
  LEFT JOIN (
    SELECT
      cp.contract_id,
      MAX(happen_date) AS capital_date
    FROM
      capitals cp 
    GROUP BY
      cp.contract_id
  )  AS cpc ON cpc.contract_id = ct.contract_id
  LEFT JOIN (
    SELECT
      ct.contract_id,
      MAX(ps.settlement_date) AS settlement_date
    FROM
      project_settlements ps 
      , contracts ct 
      , projects pj 
    WHERE
      pj.contract_id = ct.contract_id
      AND ps.project_id = pj.project_id
    GROUP BY
      ct.contract_id
  )  AS pjc ON pjc.contract_id = ct.contract_id
  LEFT JOIN (
    SELECT
      COALESCE(SUM(borrow_money),0) AS borrow_money,
      contract_id
    FROM
      capitals
    WHERE
      contract_id > 0
      AND record_receive_capital = TRUE
    GROUP BY
      contract_id
  )  AS cp ON cp.contract_id = ct.contract_id
  LEFT JOIN attachment_manages am ON am.contract_id = ct.contract_id
  LEFT JOIN (
    SELECT
      aa.contract_id,
      (aa.amount - COALESCE(bb.amount,0)) AS amount
    FROM
      (
        SELECT
          SUM(invoice_amount) AS amount,
          contract_id
        FROM
          invoices
        WHERE
          sign != 4
        GROUP BY
          contract_id
      )  AS aa 
      LEFT JOIN (
        SELECT
          SUM(invoice_amount) AS amount,
          contract_id
        FROM
          invoices
        WHERE
          sign = 3
        GROUP BY
          contract_id
      )  AS bb ON aa.contract_id = bb.contract_id
  )  AS aa ON aa.contract_id = ct.contract_id
  LEFT JOIN (
    SELECT
      aa.contract_id,
      SUM(aa.total_tax_revenue) AS amount
    FROM
      (
        SELECT
          pj.contract_id,
          rsr.total_tax_revenue
        FROM
          projects pj 
          LEFT JOIN revenue_statistical_records rsr ON rsr.project_id = pj.project_id
            AND rsr.year = year(now ())
            AND rsr.month = month(now ())
        WHERE
          pj.contract_id > 0
          AND pj.project_id IN (
              SELECT
                project_id
              FROM
                revenue_statistical_records
            )
      )  AS aa 
    GROUP BY
      aa.contract_id
  )  AS bb ON bb.contract_id = ct.contract_id
  LEFT JOIN (
    SELECT
      contract_id,
      COALESCE(SUM(return_amount),0) AS amount
    FROM
      attachment_manages
    GROUP BY
      contract_id
  )  AS am1 ON am1.contract_id = ct.contract_id
  LEFT JOIN (
    SELECT
      COUNT(1) AS count,
      amm.contract_id
    FROM
      attachment_manages amm 
    WHERE
      amm.delete_flag = 0
    GROUP BY
      amm.contract_id
  )  AS amm ON ct.contract_id = amm.contract_id
  LEFT JOIN (
    SELECT
      SUM(borrow_money) AS borrow_money,
      contract_id
    FROM
      capitals
    WHERE
      contract_id > 0
      AND happen_date >= ?
      AND happen_date < ?
    GROUP BY
      contract_id
  )  AS cpbm ON cpbm.contract_id = ct.contract_id
  LEFT JOIN (
    SELECT
      aa.contract_id,
      (aa.amount - COALESCE(bb.amount,0)) AS amount
    FROM
      (
        SELECT
          SUM(invoice_amount) AS amount,
          contract_id
        FROM
          invoices
        WHERE
          sign != 4
          AND invoice_date >= ?
          AND invoice_date < ?
        GROUP BY
          contract_id
      )  AS aa 
      LEFT JOIN (
        SELECT
          SUM(invoice_amount) AS amount,
          contract_id
        FROM
          invoices
        WHERE
          sign = 3
          AND invoice_date >= ?
          AND invoice_date < ?
        GROUP BY
          contract_id
      )  AS bb ON aa.contract_id = bb.contract_id
  )  AS inac ON inac.contract_id = ct.contract_id
WHERE
  ct.contract_info_audited = ?
  AND ct.contract_id = ?
  AND ct.contract_code LIKE ?
  AND ct.contract_name LIKE ?
  AND ct.contract_status = ?
  AND ct.contract_type = ?
  AND am.attachment_address LIKE ?
  AND am.attachment_code LIKE ?
  AND am.attachment_manage_id = ?
  AND am.return_amount = ?
  AND am.amount = ?
  AND am.attachment_name LIKE ?
  AND cp.borrow_money = ?
  AND ct.business_type = ?
  AND ct.info_code LIKE ?
  AND ct.flow_status = ?
  AND ct.is_finish = ?
  AND ct.is_system_add = ?
  AND ct.host_product_line = ?
  AND ct.design_team = ?
  AND ct.contract_id IN (
      SELECT
        contract_id
      FROM
        projects
      WHERE
        project_manage_id IN (0)
    )
