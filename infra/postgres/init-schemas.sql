-- One schema per Bounded Context, all in the same PostgreSQL instance.
-- Each service's Hibernate config targets its own schema.

CREATE SCHEMA IF NOT EXISTS ciam;
CREATE SCHEMA IF NOT EXISTS sales;
CREATE SCHEMA IF NOT EXISTS support;
CREATE SCHEMA IF NOT EXISTS billing;
CREATE SCHEMA IF NOT EXISTS marketing;
CREATE SCHEMA IF NOT EXISTS communication;

-- Grant access to the CRM user
GRANT ALL PRIVILEGES ON SCHEMA ciam TO crm;
GRANT ALL PRIVILEGES ON SCHEMA sales TO crm;
GRANT ALL PRIVILEGES ON SCHEMA support TO crm;
GRANT ALL PRIVILEGES ON SCHEMA billing TO crm;
GRANT ALL PRIVILEGES ON SCHEMA marketing TO crm;
GRANT ALL PRIVILEGES ON SCHEMA communication TO crm;
