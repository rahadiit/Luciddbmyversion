-- $Id$
-- This script installs Firewater system objects

set schema 'sys_boot.sys_boot';
set path 'sys_boot.sys_boot';

create or replace local data wrapper sys_firewater_distributed_wrapper
library 'plugin/firewater.jar'
language java;

create or replace server sys_firewater_data_server
local data wrapper sys_firewater_distributed_wrapper
options (
    driver_class 'net.sf.firewater.jdbc.FirewaterEmbeddedStorageDriver',
    url 'jdbc:firewater_storage:embedded:',
    user_name 'sa',
    qualifying_catalog_name 'SYS_FIREWATER');

create or replace foreign data wrapper sys_firewater_embedded_wrapper
library '${FARRAGO_HOME}/plugin/FarragoMedJdbc.jar'
language java
options(
  driver_class 'net.sf.firewater.jdbc.FirewaterEmbeddedStorageDriver',
  url 'jdbc:firewater_storage:embedded:'
);

-- TODO:  move this to test harness init script
create or replace foreign data wrapper sys_firewater_fakeremote_wrapper
library '${FARRAGO_HOME}/plugin/FarragoMedJdbc.jar'
language java
options(
  driver_class 'net.sf.firewater.jdbc.FirewaterEmbeddedStorageDriver',
  url 'jdbc:firewater_storage:embedded:'
);

create or replace foreign data wrapper sys_firewater_remote_wrapper
library '${FARRAGO_HOME}/plugin/FarragoMedJdbc.jar'
language java
options(
  driver_class 'net.sf.firewater.jdbc.FirewaterRemoteStorageDriver',
  url 'jdbc:firewater_storage:remote:rmi:'
);

create or replace server sys_firewater_embedded_server
foreign data wrapper sys_firewater_embedded_wrapper
options (user_name 'sa');