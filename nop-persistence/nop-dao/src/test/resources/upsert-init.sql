create table upsert_target(
  id VARCHAR(64) not null,
  scope VARCHAR(32) not null,
  val INTEGER,
  label VARCHAR(100),
  constraint pk_upsert_target primary key (id, scope)
);

create table upsert_checked(
  id VARCHAR(64) not null,
  val INTEGER not null check (val >= 0),
  constraint pk_upsert_checked primary key (id)
);
