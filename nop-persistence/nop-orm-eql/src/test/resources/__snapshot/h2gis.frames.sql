-- query: rows inline
select 
  o.SID as c1 ,
  sum(o.SID) over(  partition by 
    o.NAME
   order by 
    o.SID asc 
   rows between unbounded preceding and current row)  as s
from 
  APP_USER as o 


-- query: rows named
select 
  o.SID as c1 ,
  sum(o.SID) over w as s
from 
  APP_USER as o 


window w as ( partition by 
  o.NAME
 order by 
  o.SID asc 
 rows between unbounded preceding and current row )

-- query: range inline
select 
  o.SID as c1 ,
  sum(o.SID) over(  partition by 
    o.NAME
   order by 
    o.SID asc 
   range between unbounded preceding and current row)  as s
from 
  APP_USER as o 


-- query: range named
select 
  o.SID as c1 ,
  sum(o.SID) over w as s
from 
  APP_USER as o 


window w as ( partition by 
  o.NAME
 order by 
  o.SID asc 
 range between unbounded preceding and current row )

-- query: groups inline
select 
  o.SID as c1 ,
  sum(o.SID) over(  partition by 
    o.NAME
   order by 
    o.SID asc 
   groups between 1 preceding and current row)  as s
from 
  APP_USER as o 


-- query: groups named
select 
  o.SID as c1 ,
  sum(o.SID) over w as s
from 
  APP_USER as o 


window w as ( partition by 
  o.NAME
 order by 
  o.SID asc 
 groups between 1 preceding and current row )

-- query: named no frame
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
