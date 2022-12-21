# --- !Ups

CREATE TABLE sm_device
(
    id             SERIAL             NOT NULL,
    uid            VARCHAR            NOT NULL,
    name           VARCHAR            NOT NULL,
    label_v        VARCHAR            NOT NULL,
    name_v         VARCHAR,
    description    VARCHAR,
    visible        BOOL DEFAULT TRUE  NOT NULL,
    reliable       BOOL DEFAULT TRUE  NOT NULL,
    path_scan_date TIMESTAMP,
    crc_date       TIMESTAMP,
    exif_date      TIMESTAMP,
    job_path_scan  BOOL DEFAULT FALSE NOT NULL,
    job_calc_crc   BOOL DEFAULT FALSE NOT NULL,
    job_calc_exif  BOOL DEFAULT FALSE NOT NULL,
    job_resize     BOOL DEFAULT FALSE NOT NULL,
    CONSTRAINT sm_device_pkey PRIMARY KEY (id),
    CONSTRAINT idx_sm_device_device_uid UNIQUE (uid)
);

CREATE TABLE sm_file_card
(
    id                   VARCHAR   NOT NULL,
    device_uid           VARCHAR   NOT NULL,
    f_parent             VARCHAR   NOT NULL,
    f_name               VARCHAR   NOT NULL,
    f_extension          VARCHAR,
    f_creation_date      TIMESTAMP NOT NULL,
    f_last_modified_date TIMESTAMP NOT NULL,
    f_size               BIGINT,
    f_mime_type_java     VARCHAR,
    sha256               VARCHAR,
    f_name_lc            VARCHAR   NOT NULL,
    CONSTRAINT sm_file_card_pkey PRIMARY KEY (id),
    CONSTRAINT fk_sm_file_card_sm_device FOREIGN KEY (device_uid) REFERENCES sm_device (uid) ON DELETE RESTRICT
);

CREATE INDEX idx_f_parent
    ON sm_file_card (f_parent);

CREATE INDEX idx_fc_f_name_lc
    ON sm_file_card (f_name_lc);

CREATE INDEX idx_last_modified
    ON sm_file_card (f_last_modified_date);

CREATE INDEX idx_sm_file_card_device_uid
    ON sm_file_card (device_uid, f_parent);

CREATE INDEX idx_sha256
    ON sm_file_card (sha256);

CREATE INDEX idx_fc_sha_name
    ON sm_file_card (sha256, f_name);


CREATE INDEX idx_fc_dev_sha_name
    ON sm_file_card (device_uid, sha256, f_name);

CREATE INDEX idx_fc_parent_name
    ON sm_file_card (f_parent, f_name);


# --- !Downs

DROP TABLE IF EXISTS sm_file_card CASCADE;

DROP TABLE IF EXISTS sm_device CASCADE;
