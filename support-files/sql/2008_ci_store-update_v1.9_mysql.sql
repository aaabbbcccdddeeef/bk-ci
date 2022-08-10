USE devops_ci_store;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS ci_store_schema_update;

DELIMITER <CI_UBF>

CREATE PROCEDURE ci_store_schema_update()
BEGIN

    DECLARE db VARCHAR(100);
    SET AUTOCOMMIT = 0;
    SELECT DATABASE() INTO db;
		
	IF NOT EXISTS(SELECT 1
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_ATOM_FEATURE'
                    AND COLUMN_NAME = 'CERTIFICATION_FLAG') THEN
        ALTER TABLE T_ATOM_FEATURE ADD COLUMN `CERTIFICATION_FLAG` bit(1) DEFAULT b'0' COMMENT '是否认证标识 true：是，false：否';
    END IF;
	
	IF NOT EXISTS(SELECT 1
			  FROM information_schema.statistics
			  WHERE TABLE_SCHEMA = db
				 AND TABLE_NAME = 'T_ATOM_FEATURE'
				 AND INDEX_NAME = 'inx_taf_quality_flag') THEN
		ALTER TABLE T_ATOM_FEATURE ADD INDEX `inx_taf_quality_flag` (`QUALITY_FLAG`); 
	END IF;
	
	IF NOT EXISTS(SELECT 1
			  FROM information_schema.statistics
			  WHERE TABLE_SCHEMA = db
				 AND TABLE_NAME = 'T_ATOM_FEATURE'
				 AND INDEX_NAME = 'inx_taf_certification_flag') THEN
		ALTER TABLE T_ATOM_FEATURE ADD INDEX `inx_taf_certification_flag` (`CERTIFICATION_FLAG`); 
	END IF;

    IF NOT EXISTS(SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = db
                AND TABLE_NAME = 'T_ATOM'
                AND COLUMN_NAME = 'PUBLISHER') THEN
        ALTER TABLE T_ATOM MODIFY COLUMN `PUBLISHER` varchar(1024) NOT NULL DEFAULT 'system' COMMENT '插件发布者,对应T_STORE_PUBLISHER_INFO表的PUBLISHER_NAME字段';
    END IF;

    IF NOT EXISTS(SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = db
                AND TABLE_NAME = 'T_STORE_DOCKING_PLATFORM'
                AND COLUMN_NAME = 'OWNERS') THEN
        ALTER TABLE T_STORE_DOCKING_PLATFORM ADD COLUMN `OWNERS` varchar(1024) NOT NULL COMMENT '运营负责人';
    END IF;

    IF NOT EXISTS(SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = db
                AND TABLE_NAME = 'T_STORE_DOCKING_PLATFORM'
                AND COLUMN_NAME = 'OWNER_DEPT_NAME') THEN
        ALTER TABLE T_STORE_DOCKING_PLATFORM ADD COLUMN `OWNER_DEPT_NAME` varchar(256) NOT NULL COMMENT '所属机构名称';
    END IF;

    IF NOT EXISTS(SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = db
                AND TABLE_NAME = 'T_STORE_DOCKING_PLATFORM'
                AND COLUMN_NAME = 'LABELS') THEN
        ALTER TABLE T_STORE_DOCKING_PLATFORM ADD COLUMN `LABELS` varchar(1024) NOT NULL COMMENT '标签列表';
    END IF;

    COMMIT;
END <CI_UBF>
DELIMITER ;
COMMIT;
CALL ci_store_schema_update();