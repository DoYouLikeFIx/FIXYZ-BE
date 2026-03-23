UPDATE orders
   SET status = CASE
                    WHEN COALESCE(executed_qty, 0) > 0 THEN 'PARTIALLY_FILLED'
                    ELSE 'NEW'
                END
 WHERE order_type = 'LIMIT'
   AND status IN ('ACCEPTED', 'PENDING_NEW')
   AND COALESCE(leaves_qty, order_qty - COALESCE(executed_qty, 0)) > 0;
