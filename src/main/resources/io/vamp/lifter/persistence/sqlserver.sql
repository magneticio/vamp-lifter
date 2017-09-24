
if not exists (select * from sysobjects where name='$table' and xtype='U')
    create table $table (
      ID BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
      Content VARCHAR(MAX)
    );
