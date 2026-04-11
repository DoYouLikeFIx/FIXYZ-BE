CREATE INDEX idx_orders_book_lookup_sell
    ON orders(symbol, side, order_type, status, order_price ASC, created_at ASC, id ASC);

CREATE INDEX idx_orders_book_lookup_buy
    ON orders(symbol, side, order_type, status, order_price DESC, created_at ASC, id ASC);
