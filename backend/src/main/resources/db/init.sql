-- 设置字符集
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 创建数据库
CREATE DATABASE IF NOT EXISTS bookstore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bookstore;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    avatar VARCHAR(500),
    phone VARCHAR(20),
    role VARCHAR(20) DEFAULT 'USER',
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 分类表
CREATE TABLE IF NOT EXISTS categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    icon VARCHAR(100),
    sort_order INT DEFAULT 0,
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 书籍表
CREATE TABLE IF NOT EXISTS books (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(100),
    isbn VARCHAR(20),
    publisher VARCHAR(100),
    description TEXT,
    cover_image VARCHAR(500),
    original_price DECIMAL(10,2),
    price DECIMAL(10,2) NOT NULL,
    quality VARCHAR(20),
    stock INT DEFAULT 1,
    category_id BIGINT,
    seller_id BIGINT,
    status INT DEFAULT 1,
    view_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (seller_id) REFERENCES users(id)
);

-- 购物车表
CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    quantity INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (book_id) REFERENCES books(id)
);

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    address VARCHAR(500),
    phone VARCHAR(20),
    receiver VARCHAR(50),
    remark TEXT,
    tracking_no VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 订单项表
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (book_id) REFERENCES books(id)
);

-- 收藏表
CREATE TABLE IF NOT EXISTS favorites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (book_id) REFERENCES books(id),
    UNIQUE KEY unique_favorite (user_id, book_id)
);

-- 插入管理员和示例用户 (密码都是 123456，使用BCrypt加密)
INSERT INTO users (email, password, nickname, role, status) VALUES 
('admin@bookstore.com', '$2a$10$MnOoQ6juM5gTy4LZXfBxJOg2A/9CSSswvJcx7yccNvzJJuKEgjivG', '管理员', 'ADMIN', 1),
('user@bookstore.com', '$2a$10$MnOoQ6juM5gTy4LZXfBxJOg2A/9CSSswvJcx7yccNvzJJuKEgjivG', '测试用户', 'USER', 1),
('seller@bookstore.com', '$2a$10$MnOoQ6juM5gTy4LZXfBxJOg2A/9CSSswvJcx7yccNvzJJuKEgjivG', '书籍卖家', 'USER', 1);

-- 插入分类数据
INSERT INTO categories (id, name, icon, sort_order, status) VALUES 
(1, '文学小说', 'book', 1, 1),
(2, '教材教辅', 'graduation-cap', 2, 1),
(3, '计算机', 'laptop', 3, 1),
(4, '经济管理', 'chart-line', 4, 1),
(5, '人文社科', 'users', 5, 1),
(6, '外语学习', 'globe', 6, 1),
(7, '考试辅导', 'edit', 7, 1),
(8, '生活休闲', 'coffee', 8, 1);

-- 插入示例书籍
INSERT INTO books (title, author, isbn, publisher, description, cover_image, original_price, price, quality, stock, category_id, seller_id, status, view_count) VALUES 
('活着', '余华', '9787506365437', '作家出版社', '讲述了农村人福贵悲惨的人生遭遇。福贵本是个阔少爷，可他嗜赌如命，终于赌光了家业，一贫如洗。穷困之中的福贵因为母亲生病前去求医，没想到半路上被国民党部队抓了壮丁，后被解放军所俘虏，回到家乡他才知道母亲已经去世。', '/covers/huozhe.jpg', 45.00, 25.00, '九成新', 3, 1, 2, 1, 128),
('三体', '刘慈欣', '9787536692930', '重庆出版社', '文化大革命如火如荼进行的同时，军方探寻外星文明的绝秘计划"红岸工程"取得了突破性进展。但在按下发射键的那一刻，历经劫难的叶文洁没有意识到，她彻底改变了人类的命运。', '/covers/santi.jpg', 68.00, 35.00, '八成新', 2, 1, 2, 1, 256),
('Java编程思想', 'Bruce Eckel', '9787111213826', '机械工业出版社', 'Java学习经典之作，从Java的基础语法到最高级特性，适合各层次Java程序员阅读。本书能够帮助你深入理解Java语言和编程思想。', '/covers/java.jpg', 108.00, 45.00, '七成新', 5, 3, 3, 1, 89),
('人类简史', '尤瓦尔·赫拉利', '9787508647357', '中信出版社', '从十万年前有生命迹象开始到21世纪资本、科技交织的人类发展史。这是一部宏大的人类简史，理清了影响人类发展的重大脉络。', '/covers/renleijiashi.jpg', 68.00, 30.00, '八成新', 3, 5, 3, 1, 203),
('高等数学（第七版）上册', '同济大学', '9787040396638', '高等教育出版社', '经典高数教材，适合理工科学生使用。内容涵盖函数与极限、导数与微分、微分中值定理与导数的应用等。', '/covers/gaoshu.jpg', 38.00, 18.00, '八成新', 8, 2, 2, 1, 342),
('Python编程从入门到实践', 'Eric Matthes', '9787115428028', '人民邮电出版社', '一本针对所有层次Python读者而作的Python入门书。全书分两部分：基础知识和项目实践。', '/covers/python.jpg', 89.00, 42.00, '九成新', 6, 3, 3, 1, 178),
('百年孤独', '加西亚·马尔克斯', '9787544253994', '南海出版公司', '魔幻现实主义文学的代表作，描写了布恩迪亚家族七代人的传奇故事，以及加勒比海沿岸小镇马孔多的百年兴衰。被誉为"再现拉丁美洲历史社会图景的鸿篇巨著"。', '/covers/huozhe.jpg', 55.00, 28.00, '八成新', 4, 1, 3, 1, 167),
('数据结构与算法分析', 'Mark Allen Weiss', '9787111521143', '机械工业出版社', '经典计算机科学教材，系统介绍了常用的数据结构和算法分析方法，涵盖表、栈、队列、树、散列、优先队列、排序、图论等核心内容。', '/covers/java.jpg', 79.00, 35.00, '七成新', 3, 3, 2, 1, 95),
('经济学原理（微观经济学分册）', '曼昆', '9787301150894', '北京大学出版社', '全球最受欢迎的经济学入门教材之一。曼昆以浅显易懂的方式阐述经济学的基本原理，从供求关系到市场效率，帮助读者建立经济学思维。', '/covers/renleijiashi.jpg', 72.00, 32.00, '九成新', 5, 4, 3, 1, 186),
('新概念英语2：实践与进步', 'L.G.Alexander', '9787560013466', '外语教学与研究出版社', '经典英语学习教材，适合有一定英语基础的学习者。通过96篇课文和丰富的练习，帮助学生掌握英语听说读写能力。', '/covers/gaoshu.jpg', 42.00, 20.00, '八成新', 10, 6, 2, 1, 278),
('考研英语历年真题详解', '张剑', '9787501256789', '世界知识出版社', '涵盖近20年考研英语真题，逐题详细解析，分析命题思路与解题技巧。适合考研备考学生系统复习使用。', '/covers/python.jpg', 68.00, 30.00, '九成新', 6, 7, 2, 1, 412),
('小王子', '安托万·德·圣-埃克苏佩里', '9787020042494', '人民文学出版社', '以一位飞行员作为故事叙述者，讲述了小王子从自己星球出发前往地球的过程中，所经历的各种历险。这是一本关于爱与责任的经典童话。', '/covers/santi.jpg', 32.00, 15.00, '全新', 7, 8, 3, 1, 320);

-- 插入示例订单
INSERT INTO orders (order_no, user_id, total_amount, status, address, phone, receiver, remark) VALUES
('ORD20240101001', 2, 60.00, 'COMPLETED', '北京市海淀区中关村大街1号', '13800138001', '张三', '请尽快发货'),
('ORD20240102002', 2, 45.00, 'SHIPPED', '上海市浦东新区陆家嘴环路1000号', '13800138002', '李四', NULL),
('ORD20240103003', 3, 60.00, 'PAID', '广州市天河区天河路385号', '13800138003', '王五', '周末送货');

-- 插入订单项
INSERT INTO order_items (order_id, book_id, quantity, price) VALUES
(1, 1, 1, 25.00),
(1, 2, 1, 35.00),
(2, 3, 1, 45.00),
(3, 5, 1, 18.00),
(3, 6, 1, 42.00);
