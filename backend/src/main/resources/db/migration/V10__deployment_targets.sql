-- Phase 10: Kubernetes as a second deployment target, chosen per environment.

ALTER TABLE deployment_configs
    ADD COLUMN target varchar(20) NOT NULL DEFAULT 'DOCKER';
ALTER TABLE deployment_configs ADD CONSTRAINT ck_deployment_configs_target
    CHECK (target IN ('DOCKER', 'KUBERNETES'));

-- The target a deployment ran on; container_id holds a Kubernetes workload reference for
-- KUBERNETES deployments.
ALTER TABLE deployments
    ADD COLUMN target varchar(20) NOT NULL DEFAULT 'DOCKER';
ALTER TABLE deployments ADD CONSTRAINT ck_deployments_target
    CHECK (target IN ('DOCKER', 'KUBERNETES'));
