-- query: rows inline
select 
  o.sid as c1 ,
  sum(o.sid) over(  partition by 
    o.name
   order by 
    o.sid asc 
   rows between unbounded preceding and current row)  as s
from 
  app_user as o 


-- query: rows named
select 
  o.sid as c1 ,
  sum(o.sid) over w as s
from 
  app_user as o 


window w as ( partition by 
  o.name
 order by 
  o.sid asc 
 rows between unbounded preceding and current row )

-- query: range inline
select 
  o.sid as c1 ,
  sum(o.sid) over(  partition by 
    o.name
   order by 
    o.sid asc 
   range between unbounded preceding and current row)  as s
from 
  app_user as o 


-- query: range named
select 
  o.sid as c1 ,
  sum(o.sid) over w as s
from 
  app_user as o 


window w as ( partition by 
  o.name
 order by 
  o.sid asc 
 range between unbounded preceding and current row )

-- query: groups inline
select 
  o.sid as c1 ,
  sum(o.sid) over(  partition by 
    o.name
   order by 
    o.sid asc 
   groups between 1 preceding and current row)  as s
from 
  app_user as o 


-- query: groups named
select 
  o.sid as c1 ,
  sum(o.sid) over w as s
from 
  app_user as o 


window w as ( partition by 
  o.name
 order by 
  o.sid asc 
 groups between 1 preceding and current row )

-- query: named no frame
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
