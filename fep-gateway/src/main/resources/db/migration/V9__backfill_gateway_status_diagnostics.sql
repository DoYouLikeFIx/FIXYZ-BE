UPDATE gateway_orders
SET status_message = 'execution state is unresolved in external system'
WHERE status = 'UNKNOWN'
  AND (status_message IS NULL OR TRIM(status_message) = '');

UPDATE gateway_orders
SET status_message = 'execution report is still pending'
WHERE status = 'PENDING'
  AND (status_message IS NULL OR TRIM(status_message) = '');

UPDATE gateway_orders
SET reject_reason = 'OTHER'
WHERE status = 'REJECTED'
  AND (reject_reason IS NULL OR TRIM(reject_reason) = '');

UPDATE gateway_orders
SET status_message = 'FIX ExecutionReport parse failed; manual review required'
WHERE status = 'MALFORMED'
  AND (status_message IS NULL OR TRIM(status_message) = '');

UPDATE gateway_orders
SET parse_error = 'PARSE_ERROR:LEGACY_STATUS_ROW'
WHERE status = 'MALFORMED'
  AND (parse_error IS NULL OR TRIM(parse_error) = '');
