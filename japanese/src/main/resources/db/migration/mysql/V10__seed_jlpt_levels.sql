-- JLPT-MAX Ticket 4E-5: additive seed of the standard JLPT (system=JLPT) N5-N1 Level catalog.
--
-- Each row is inserted ONLY when zero rows with that exact (level_system, code) pair already exist.
-- There is deliberately no UNIQUE constraint on (level_system, code) (out of scope for this ticket -
-- see Ticket 4E-5's own report), so this migration never overwrites, deletes, or reassigns the id of
-- any existing row, and never adds a row for a key that already has one or more rows (including a
-- pre-existing duplicate key) - it only fills in genuinely missing canonical keys. Safe to run against
-- a database with zero, some, or all five JLPT rows already present.

insert into levels (level_system, code, name)
select 'JLPT', 'N5', 'JLPT N5'
where not exists (select 1 from levels where level_system = 'JLPT' and code = 'N5');

insert into levels (level_system, code, name)
select 'JLPT', 'N4', 'JLPT N4'
where not exists (select 1 from levels where level_system = 'JLPT' and code = 'N4');

insert into levels (level_system, code, name)
select 'JLPT', 'N3', 'JLPT N3'
where not exists (select 1 from levels where level_system = 'JLPT' and code = 'N3');

insert into levels (level_system, code, name)
select 'JLPT', 'N2', 'JLPT N2'
where not exists (select 1 from levels where level_system = 'JLPT' and code = 'N2');

insert into levels (level_system, code, name)
select 'JLPT', 'N1', 'JLPT N1'
where not exists (select 1 from levels where level_system = 'JLPT' and code = 'N1');
