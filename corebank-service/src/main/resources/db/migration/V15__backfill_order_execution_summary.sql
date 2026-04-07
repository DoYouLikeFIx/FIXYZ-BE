UPDATE orders
SET executed_qty = 0.0000
WHERE executed_qty IS NULL;

UPDATE orders
SET leaves_qty = GREATEST(order_qty - COALESCE(executed_qty, 0.0000), 0.0000)
WHERE leaves_qty IS NULL;
