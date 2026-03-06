-- H2 集成测试 Schema（对应 TestUser 实体）
CREATE TABLE IF NOT EXISTS `user` (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(100) NOT NULL,
    delete_flag INT          NOT NULL DEFAULT 0,
    tenant_id   BIGINT
);
