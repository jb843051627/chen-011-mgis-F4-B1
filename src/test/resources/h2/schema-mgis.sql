-- H2（MODE=MySQL）下的 mgis 最小表结构，仅用于业务层集成测试。
-- 列与实体 @TableField 对齐。

CREATE TABLE t_mgis_unit_doc (
  id BIGINT NOT NULL,
  site_no VARCHAR(64),
  site_name VARCHAR(128),
  site_type VARCHAR(32),
  road_name VARCHAR(128),
  th1_max DECIMAL(12,2),
  th2_max DECIMAL(12,2),
  th3_max DECIMAL(12,2),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_mgis_scope_card (
  id BIGINT NOT NULL,
  bill_no VARCHAR(64),
  bill_date DATE,
  site_id INT,
  site_no VARCHAR(64),
  qty DECIMAL(12,2),
  fine_amt DECIMAL(12,2),
  grade_level INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  UNIQUE (bill_no)
);

CREATE TABLE t_mgis_check_card (
  id BIGINT NOT NULL,
  biz_no VARCHAR(64),
  stage INT,
  status INT,
  content VARCHAR(255),
  last_action VARCHAR(64),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_mgis_use_bill (
  id BIGINT NOT NULL,
  bill_no VARCHAR(64),
  node_no INT,
  sign_mode INT,
  need_count INT,
  sign_count INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_mgis_submit_row (
  id BIGINT NOT NULL,
  batch_no VARCHAR(64),
  row_no INT,
  item_code VARCHAR(64),
  qty DECIMAL(12,2),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_mgis_duty_task (
  id BIGINT NOT NULL,
  item_no VARCHAR(64),
  due_at TIMESTAMP,
  amount DECIMAL(12,2),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_mgis_qual_line (
  id BIGINT NOT NULL,
  rule_code VARCHAR(64),
  rule_name VARCHAR(128),
  th1_max DECIMAL(12,2),
  th2_max DECIMAL(12,2),
  th3_max DECIMAL(12,2),
  eff_start TIMESTAMP,
  eff_end TIMESTAMP,
  priority INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time TIMESTAMP,
  update_by VARCHAR(64),
  update_time TIMESTAMP,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);
