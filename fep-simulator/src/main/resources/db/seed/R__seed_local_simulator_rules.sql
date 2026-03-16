INSERT INTO simulator_rules (
	rule_id,
	rule_action,
	target_symbol,
	target_exchange,
	ttl_seconds,
	match_amount,
	probability,
	applied_at,
	expires_at,
	created_at,
	updated_at,
	version
)
VALUES (
	'rule-default-approve',
	'APPROVE',
	NULL,
	'KRX',
	31536000,
	NULL,
	1.0,
	CURRENT_TIMESTAMP(6),
	TIMESTAMPADD(SECOND, 31536000, CURRENT_TIMESTAMP(6)),
	CURRENT_TIMESTAMP(6),
	CURRENT_TIMESTAMP(6),
	0
);
