-- Phase 9: container image vulnerability scanning results on deployments.

ALTER TABLE deployments
    ADD COLUMN scan_status              varchar(20),
    ADD COLUMN vulnerabilities_critical integer,
    ADD COLUMN vulnerabilities_high     integer;

ALTER TABLE deployments ADD CONSTRAINT ck_deployments_scan_status
    CHECK (scan_status IN ('PASSED', 'VULNERABLE', 'BLOCKED', 'ERROR', 'SKIPPED'));

ALTER TABLE deployment_logs DROP CONSTRAINT ck_deployment_logs_phase;
ALTER TABLE deployment_logs ADD CONSTRAINT ck_deployment_logs_phase
    CHECK (phase IN ('SOURCE', 'BUILD', 'SCAN', 'PUSH', 'DEPLOY', 'HEALTH_CHECK', 'RUNTIME'));
