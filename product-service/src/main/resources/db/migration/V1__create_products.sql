CREATE TABLE products (
    id    BIGINT PRIMARY KEY,
    name  VARCHAR(100)   NOT NULL,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0)
);
