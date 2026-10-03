-- dialect: postgis
-- query: over
select 
  o.sid as c1 ,
  sum(o.sid) over(  partition by 
    o.name
   order by 
    o.sid asc 
  )  as s
from 
  app_user as o 


-- query: window
select 
  o.sid as c1 ,
  sum(o.sid) over w as s
from 
  app_user as o 


window w as ( partition by 
  o.name
 order by 
  o.sid asc 
 )
