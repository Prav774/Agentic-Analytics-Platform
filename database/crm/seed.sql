\set ON_ERROR_STOP on
\connect enterprise_crm

BEGIN;

INSERT INTO customers (id, name, email, company, industry, created_at)
SELECT g,
       'Customer ' || lpad(g::TEXT, 2, '0'),
       'customer' || lpad(g::TEXT, 2, '0') || '@example.com',
       (ARRAY['Northstar', 'Everfield', 'Bluepeak', 'Redtail', 'Westbridge', 'Silverline'])[((g - 1) % 6) + 1]
           || ' ' || lpad(g::TEXT, 2, '0') || ' Group',
       (ARRAY['Technology', 'Healthcare', 'Finance', 'Manufacturing', 'Retail', 'Professional Services'])[((g - 1) % 6) + 1],
       TIMESTAMPTZ '2024-01-01 09:00:00+00' + ((g - 1) * 13) * INTERVAL '1 day'
FROM generate_series(1, 24) AS g
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    email = EXCLUDED.email,
    company = EXCLUDED.company,
    industry = EXCLUDED.industry,
    created_at = EXCLUDED.created_at;

INSERT INTO leads (id, customer_id, lead_source, status, assigned_to, estimated_value, created_at)
SELECT g,
       ((g - 1) % 24) + 1,
       (ARRAY['website', 'referral', 'trade_show', 'partner', 'outbound'])[((g - 1) % 5) + 1],
       (ARRAY['new', 'qualified', 'nurturing', 'converted', 'disqualified'])[((g - 1) % 5) + 1],
       (ARRAY['Alex Morgan', 'Jamie Rivera', 'Taylor Kim', 'Riley Singh'])[((g - 1) % 4) + 1],
       (2500 + ((g * 1375) % 48000))::NUMERIC(12, 2),
       TIMESTAMPTZ '2024-01-05 10:00:00+00' + ((g - 1) * 11) * INTERVAL '1 day'
FROM generate_series(1, 48) AS g
ON CONFLICT (id) DO UPDATE SET
    customer_id = EXCLUDED.customer_id,
    lead_source = EXCLUDED.lead_source,
    status = EXCLUDED.status,
    assigned_to = EXCLUDED.assigned_to,
    estimated_value = EXCLUDED.estimated_value,
    created_at = EXCLUDED.created_at;

INSERT INTO opportunities (id, customer_id, opportunity_name, stage, amount, expected_close_date, created_at)
SELECT g,
       ((g - 1) % 24) + 1,
       'Enterprise Renewal ' || lpad(g::TEXT, 3, '0'),
       (ARRAY['qualification', 'discovery', 'proposal', 'negotiation', 'closed_won', 'closed_lost'])[((g - 1) % 6) + 1],
       (12000 + ((g * 4287) % 185000))::NUMERIC(14, 2),
       (TIMESTAMPTZ '2024-01-10 09:00:00+00' + ((g - 1) * 15) * INTERVAL '1 day')::DATE + (30 + (g % 120)),
       TIMESTAMPTZ '2024-01-10 09:00:00+00' + ((g - 1) * 15) * INTERVAL '1 day'
FROM generate_series(1, 36) AS g
ON CONFLICT (id) DO UPDATE SET
    customer_id = EXCLUDED.customer_id,
    opportunity_name = EXCLUDED.opportunity_name,
    stage = EXCLUDED.stage,
    amount = EXCLUDED.amount,
    expected_close_date = EXCLUDED.expected_close_date,
    created_at = EXCLUDED.created_at;

INSERT INTO activities (id, customer_id, activity_type, description, activity_date)
SELECT g,
       ((g - 1) % 24) + 1,
       (ARRAY['call', 'email', 'meeting', 'demo', 'task'])[((g - 1) % 5) + 1],
       'Account follow-up ' || lpad(g::TEXT, 3, '0'),
       TIMESTAMPTZ '2024-01-03 11:00:00+00' + ((g - 1) * 5) * INTERVAL '1 day'
FROM generate_series(1, 120) AS g
ON CONFLICT (id) DO UPDATE SET
    customer_id = EXCLUDED.customer_id,
    activity_type = EXCLUDED.activity_type,
    description = EXCLUDED.description,
    activity_date = EXCLUDED.activity_date;

SELECT setval(pg_get_serial_sequence('public.customers', 'id'), (SELECT MAX(id) FROM customers), TRUE);
SELECT setval(pg_get_serial_sequence('public.leads', 'id'), (SELECT MAX(id) FROM leads), TRUE);
SELECT setval(pg_get_serial_sequence('public.opportunities', 'id'), (SELECT MAX(id) FROM opportunities), TRUE);
SELECT setval(pg_get_serial_sequence('public.activities', 'id'), (SELECT MAX(id) FROM activities), TRUE);

COMMIT;

SELECT 'customers' AS table_name, COUNT(*) AS row_count FROM customers
UNION ALL SELECT 'leads', COUNT(*) FROM leads
UNION ALL SELECT 'opportunities', COUNT(*) FROM opportunities
UNION ALL SELECT 'activities', COUNT(*) FROM activities
ORDER BY table_name;

-- Pipeline totals by expected close month and stage.
SELECT date_trunc('month', expected_close_date)::DATE AS close_month,
       stage,
       COUNT(*) AS opportunity_count,
       SUM(amount) AS pipeline_amount
FROM opportunities
GROUP BY close_month, stage
ORDER BY close_month, stage;

-- Customer-level rollup without multiplying rows across the one-to-many relationships.
WITH lead_totals AS (
    SELECT customer_id, COUNT(*) AS lead_count
    FROM leads
    GROUP BY customer_id
), opportunity_totals AS (
    SELECT customer_id, COUNT(*) AS opportunity_count, SUM(amount) AS pipeline_amount
    FROM opportunities
    GROUP BY customer_id
), activity_totals AS (
    SELECT customer_id, COUNT(*) AS activity_count
    FROM activities
    GROUP BY customer_id
)
SELECT c.id,
       c.company,
       COALESCE(l.lead_count, 0) AS lead_count,
       COALESCE(o.opportunity_count, 0) AS opportunity_count,
       COALESCE(o.pipeline_amount, 0) AS pipeline_amount,
       COALESCE(a.activity_count, 0) AS activity_count
FROM customers AS c
LEFT JOIN lead_totals AS l ON l.customer_id = c.id
LEFT JOIN opportunity_totals AS o ON o.customer_id = c.id
LEFT JOIN activity_totals AS a ON a.customer_id = c.id
ORDER BY c.id;
