SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = :'DB_NAME' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS :"DB_NAME";
DROP ROLE IF EXISTS :"DB_USER";