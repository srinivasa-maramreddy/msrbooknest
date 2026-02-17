CREATE TABLE IF NOT EXISTS authors (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    bio VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS books (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    isbn VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000),
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL,
    cover_image_url VARCHAR(800),
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    best_seller BOOLEAN NOT NULL DEFAULT FALSE,
    author_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_books_author FOREIGN KEY (author_id) REFERENCES authors(id),
    CONSTRAINT fk_books_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    shipping_address VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_items_book FOREIGN KEY (book_id) REFERENCES books(id)
);

CREATE INDEX idx_books_author_id ON books(author_id);
CREATE INDEX idx_books_category_id ON books(category_id);
CREATE INDEX idx_books_title ON books(title);
CREATE INDEX idx_books_featured ON books(featured);
CREATE INDEX idx_books_best_seller ON books(best_seller);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_book_id ON order_items(book_id);

INSERT INTO authors (name, bio, created_at, updated_at) VALUES
('James Clear', 'Author of Atomic Habits and productivity works.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Cal Newport', 'Author focused on deep work and digital minimalism.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Morgan Housel', 'Finance writer and author of The Psychology of Money.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO categories (name, description, created_at, updated_at) VALUES
('Self-Help', 'Personal growth and productivity books.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Business', 'Business strategy and leadership.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Finance', 'Personal finance and investing.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO books (
    isbn, title, description, price, stock, cover_image_url, featured, best_seller, author_id, category_id, created_at, updated_at
) VALUES
(
    '9781847941831',
    'Atomic Habits',
    'Tiny changes, remarkable results.',
    499.00,
    60,
    'https://images.unsplash.com/photo-1544947950-fa07a98d237f',
    TRUE,
    TRUE,
    1,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '9781455586691',
    'Deep Work',
    'Rules for focused success in a distracted world.',
    459.00,
    45,
    'https://images.unsplash.com/photo-1512820790803-83ca734da794',
    TRUE,
    FALSE,
    2,
    2,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '9789390166268',
    'The Psychology of Money',
    'Timeless lessons on wealth and behavior.',
    399.00,
    70,
    'https://images.unsplash.com/photo-1516979187457-637abb4f9353',
    FALSE,
    TRUE,
    3,
    3,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
