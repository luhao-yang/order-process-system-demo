CREATE TABLE orders (
    id            UUID            NOT NULL PRIMARY KEY,
    customer_id   VARCHAR(255)    NOT NULL,
    status        VARCHAR(32)     NOT NULL,
    total_amount  DECIMAL(19, 2)  NOT NULL,
    created_at    TIMESTAMP       NOT NULL,
    updated_at    TIMESTAMP       NOT NULL,
    version       BIGINT          NOT NULL
);

CREATE INDEX idx_orders_status      ON orders (status);
CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_created_at  ON orders (created_at);

CREATE TABLE order_items (
    order_id    UUID            NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id  VARCHAR(255)    NOT NULL,
    quantity    INTEGER         NOT NULL,
    unit_price  DECIMAL(19, 2)  NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
