-- dialect: mysql
-- query: over
select 
  o.SID as c1 ,
  sum(o.SID) over(  partition by 
    o.NAME
   order by 
    o.SID asc 
  )  as s
from 
  app_user as o 


-- query: window
select 
  o.SID as c1 ,
  sum(o.SID) over w as s
from 
  app_user as o 


window w as ( partition by 
  o.NAME
 order by 
  o.SID asc 
 )
