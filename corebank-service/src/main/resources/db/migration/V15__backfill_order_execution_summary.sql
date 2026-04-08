UPDATE orders
SET executed_qty = CASE
  WHEN leaves_qty IS NULL THEN 0.0000
  ELSE GREATEST(order_qty - leaves_qty, 0.0000)
END
WHERE executed_qty IS NULL;

UPDATE orders
SET leaves_qty = GREATEST(order_qty - executed_qty, 0.0000)
WHERE leaves_qty IS NULL;
