CREATE TABLE orders (
    id       BIGINT PRIMARY KEY,
    user_id  BIGINT       NOT NULL,
    product  VARCHAR(100) NOT NULL,
    quantity INT          NOT NULL CHECK (quantity > 0)
);
