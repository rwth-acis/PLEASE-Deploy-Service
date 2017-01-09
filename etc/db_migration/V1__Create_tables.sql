-- DROP TABLE IF EXISTS files

-- Beware of setting table and schema names into quotes, as h2 has default option DATABASE_TO_UPPER=true

CREATE TABLE build_containers (
  `app` INT NOT NULL
, `version` VARCHAR(255)
, `cid` VARCHAR(255)
, `imageid` VARCHAR(255)
, CONSTRAINT pk_build PRIMARY KEY (app,version)
);

CREATE TABLE deployment_containers (
  `ip6` VARCHAR(255) NOT NULL
, `version` VARCHAR(255)
, `cid` VARCHAR(255)
, CONSTRAINT pk_deployc PRIMARY KEY (cid)
);

CREATE TABLE deployments (
  `ip6` VARCHAR(255) NOT NULL
, `app` INT NOT NULL
, `cid` VARCHAR(255)
, CONSTRAINT pk_deploy PRIMARY KEY (ip6)
);
