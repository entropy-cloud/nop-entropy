-- dialect: h2gis
-- query: over
select 
  o.SID as c1 ,
  sum(o.SID) over(  partition by 
    o.NAME
   order by 
    o.SID asc 
  )  as s
from 
  APP_USER as o 


-- query: window
select 
  o.SID as c1 ,
  sum(o.SID) over w as s
from 
  APP_USER as o 


window w as ( partition by 
  o.NAME
 order by 
  o.SID asc 
 )
