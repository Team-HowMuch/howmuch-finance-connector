SELECT format('CREATE ROLE howmuch_finance_connector LOGIN PASSWORD %L', :'connector_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'howmuch_finance_connector') \gexec

SELECT format('ALTER ROLE howmuch_finance_connector PASSWORD %L', :'connector_password') \gexec

SELECT 'CREATE DATABASE howmuch_finance_connector OWNER howmuch_finance_connector'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'howmuch_finance_connector') \gexec
