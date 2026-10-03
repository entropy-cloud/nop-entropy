-- SQL quickstart query (WI23): running aggregate over the orders stream.
SELECT item, sum(amount) AS total FROM orders GROUP BY item
