# 二手书购买平台

## How to Run

```bash
# 启动所有服务
docker-compose up --build -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

## Services

| 服务 | 端口 | 访问地址 |
|------|------|----------|
| 用户端 | 8092 | http://localhost:8092 |
| 管理后台 | 8091 | http://localhost:8091 |
| 后端 API | 8090 | http://localhost:8090/api |
| MySQL | **3307** | localhost:**3307** |

> **注意：** MySQL 映射到宿主机的 **3307** 端口（非默认 3306），以避免与本地已有的 MySQL 实例冲突。使用数据库客户端连接时，请确认端口为 **3307**。

启动成功后直接访问：
- 用户端：http://localhost:8092
- 管理后台：http://localhost:8091

## 测试账号

### 管理后台
- 地址：http://localhost:8091
- 账号：admin@bookstore.com
- 密码：123456

### 用户端
- 地址：http://localhost:8092
- 账号：user@bookstore.com
- 密码：123456

## 测试数据说明

系统初始化时会自动创建测试数据，包括：
- 3 个测试用户（管理员、普通用户、卖家）
- 8 个书籍分类
- 6 本示例书籍（使用真实封面图片）
- 3 个示例订单

## 支付功能说明

### 支付模式

系统支持两种支付模式：

1. **模拟支付模式 (mock)** - 默认模式
   - 无需配置真实支付参数
   - 点击确认即可完成支付
   - 适用于开发和测试环境

2. **生产支付模式 (production)**
   - 接入真实的微信支付和支付宝
   - 需要配置相应的支付参数

### 支持的支付方式

| 支付方式 | 图标 | 说明 |
|---------|------|------|
| 微信支付 | ![微信](https://img.shields.io/badge/-微信支付-07C160?style=flat&logo=wechat&logoColor=white) | 推荐使用微信扫码支付 |
| 支付宝 | ![支付宝](https://img.shields.io/badge/-支付宝-1677FF?style=flat&logo=alipay&logoColor=white) | 支持花呗分期付款 |

### 生产环境配置

切换到生产支付模式，需要设置以下环境变量：

```bash
# 启用生产支付模式
export PAYMENT_MODE=production

# 微信支付配置
export WECHAT_PAY_APPID=你的微信AppID
export WECHAT_PAY_MCHID=你的商户号
export WECHAT_PAY_SECRET=你的API密钥
export WECHAT_PAY_NOTIFY_URL=https://你的域名/api/payment/wechat/notify

# 支付宝配置
export ALIPAY_APPID=你的支付宝AppID
export ALIPAY_PRIVATE_KEY=你的应用私钥
export ALIPAY_PUBLIC_KEY=支付宝公钥
export ALIPAY_NOTIFY_URL=https://你的域名/api/payment/alipay/notify

# 启动服务
docker-compose up --build -d
```

或者在 `docker-compose.yml` 同级目录创建 `.env` 文件：

```env
PAYMENT_MODE=production
WECHAT_PAY_APPID=你的微信AppID
WECHAT_PAY_MCHID=你的商户号
WECHAT_PAY_SECRET=你的API密钥
WECHAT_PAY_NOTIFY_URL=https://你的域名/api/payment/wechat/notify
ALIPAY_APPID=你的支付宝AppID
ALIPAY_PRIVATE_KEY=你的应用私钥
ALIPAY_PUBLIC_KEY=支付宝公钥
ALIPAY_NOTIFY_URL=https://你的域名/api/payment/alipay/notify
```

### 支付流程

```
用户下单 → 选择支付方式 → 创建支付订单 → 确认支付 → 支付成功 → 更新订单状态
```

1. 用户在购物车提交订单后，订单状态为"待付款"
2. 点击"立即支付"按钮，选择微信支付或支付宝
3. 模拟模式下点击确认即完成支付
4. 生产模式下会调用真实支付接口
5. 支付成功后订单状态更新为"已付款"

## 题目内容

@给我写一个二手书购买平台，用springboot+vue，并给我建立数据库，我数据库的账号和密码分别是root和1234

构建一个二手书购买平台，技术栈：
- 后端：Spring Boot 3.2 + JPA + MySQL
- 前端：Vue 3 + Vite + Pinia
- 容器化：Docker + Docker Compose

### 功能模块

#### 用户端 (http://localhost:8092)
- 用户注册/登录
- 浏览书籍列表
- 搜索书籍
- 查看书籍详情
- 购物车管理
- 下单购买
- 在线支付（微信/支付宝）
- 订单管理
- 发布二手书
- 收藏功能

#### 管理后台 ( )
- 管理员登录
- 数据统计仪表盘
- 用户管理
- 书籍管理
- 分类管理
- 订单管理

### 项目结构

```
├── backend/                 # Spring Boot 后端
│   ├── Dockerfile
│   └── src/
├── frontend-admin/          # Vue 3 管理后台
│   ├── Dockerfile
│   └── src/
├── frontend-user/           # Vue 3 用户端
│   ├── Dockerfile
│   └── src/
├── docker-compose.yml       # Docker 编排文件
├── .gitignore              # Git 忽略文件
└── README.md               # 项目说明
```

### 数据库配置

- 主机：localhost (容器内为 mysql)
- 端口：3307
- 数据库：bookstore
- 用户名：root
- 密码：1234

## 本地开发指南

如果需要在本地（非 Docker）环境进行开发调试，请按以下步骤操作：

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Maven 3.8+

### 1. 数据库配置

本地 MySQL 默认端口为 3306，需要创建数据库：

```sql
CREATE DATABASE bookstore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后导入初始化脚本：

```bash
mysql -u root -p1234 bookstore < backend/src/main/resources/db/init.sql
```

### 2. 后端启动

修改 `backend/src/main/resources/application.yml` 中的数据库配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bookstore  # 本地端口改为 3306
```

启动后端：

```bash
cd backend
mvn spring-boot:run
```

后端将在 http://localhost:8080 启动。

### 3. 前端启动

用户端：

```bash
cd frontend-user
npm install
npm run dev
```

用户端将在 http://localhost:5173 启动。

管理后台：

```bash
cd frontend-admin
npm install
npm run dev
```

管理后台将在 http://localhost:5174 启动。

### 4. 本地开发端口对照

| 服务 | Docker 端口 | 本地开发端口 |
|------|-------------|--------------|
| 后端 API | 8090 | 8080 |
| 用户端 | 8092 | 5173 |
| 管理后台 | 8091 | 5174 |
| MySQL | 3307 | 3306 |

### 5. 前端代理配置

前端 Vite 配置已设置代理，本地开发时 API 请求会自动转发到 `http://localhost:8080`。

如需修改代理目标，编辑 `vite.config.js`：

```javascript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',  // 修改为你的后端地址
      changeOrigin: true
    }
  }
}
```
