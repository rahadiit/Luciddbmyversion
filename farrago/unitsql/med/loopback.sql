-- $Id$
-- Test SQL/MED loopback link support

-- create a server which will loop back to the default catalog (localdb)
create server loopback_localdb
foreign data wrapper sys_jdbc
options(
    driver_class 'net.sf.farrago.jdbc.engine.FarragoJdbcEngineDriver',
    url 'jdbc:farrago:',
    user_name 'sa');

-- create a server which will loop back to a specific catalog (sys_boot)
create server loopback_sys_boot
foreign data wrapper sys_jdbc
options(
    driver_class 'net.sf.farrago.jdbc.engine.FarragoJdbcEngineDriver',
    url 'jdbc:farrago:',
    user_name 'sa',
    qualifying_catalog_name 'SYS_BOOT');

-- test loopback queries

select * from loopback_localdb.sales.depts order by deptno;

select * from loopback_sys_boot.jdbc_metadata.catalogs_view order by table_cat;

select emps.name as ename, depts.name as dname from 
loopback_localdb.sales.emps inner join loopback_localdb.sales.depts
on emps.deptno=depts.deptno
order by ename;

-- this should fail due to non-existent table
select * from loopback_localdb.sales.fudge order by deptno;


-- LER-3846:  use loopback to test what happens to views on foreign
-- tables when the foreign server (faked here via loopback) changes
-- column types behind our backs

create schema x;

-- simulate foreign table
create view x.foo as select 100 from (values(0));

-- create a local view which references the foreign table
create view x.bar as
select * from loopback_localdb.x.foo;

-- change the foreign table to produce a new cast-compatible type and value
create or replace view x.foo as select '  500  ' from (values(0));

-- should not fail, but should cast to original type type frozen when
-- bar was created
select * from x.bar;

-- now force a cast failure to see what happens
create or replace view x.foo as select 'foofah' from (values(0));

call sys_boot.mgmt.flush_code_cache();
select * from x.bar;

-- finally, simulate a change to a non-CAST-compatible type
-- (TODO:  need a better error message; see comments in
--  FarragoOJRexCastImplementor.java)
create or replace view x.foo as select true from (values(0));

call sys_boot.mgmt.flush_code_cache();
select * from x.bar;

-- verify loopback via EXPLAIN PLAN
!set outputformat csv

explain plan excluding attributes for 
select * from loopback_localdb.sales.depts order by deptno;

explain plan excluding attributes for
select * from loopback_sys_boot.jdbc_metadata.catalogs_view;

explain plan excluding attributes for
select emps.name as ename, depts.name as dname from 
loopback_localdb.sales.emps inner join loopback_localdb.sales.depts
on emps.deptno=depts.deptno
order by ename;
