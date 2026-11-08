-- Roles whose members may close and reopen TOPs, besides the admins. Until now the vote manager roles decided that, so
-- they are copied over to keep everyone's permissions.
CREATE TABLE top_manager_role
(
    role_id VARCHAR(32) PRIMARY KEY
);

INSERT INTO top_manager_role (role_id)
SELECT role_id
FROM vote_manager_role;
