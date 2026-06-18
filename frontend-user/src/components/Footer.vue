<template>
  <footer class="footer">
    <div class="container">
      <div class="footer-content">
        <div class="footer-brand">
          <div class="logo">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
            </svg>
            <span>二手书市场</span>
          </div>
          <p>让每一本好书找到新的主人</p>
        </div>
        <div class="footer-links">
          <div class="link-group">
            <h4>快速导航</h4>
            <router-link to="/">首页</router-link>
            <router-link to="/books">全部书籍</router-link>
            <router-link to="/publish">发布书籍</router-link>
            <router-link to="/orders">我的订单</router-link>
          </div>
          <div class="link-group">
            <h4>关于我们</h4>
            <a href="#" @click.prevent="showAbout">平台介绍</a>
            <a href="#" @click.prevent="showContact">联系我们</a>
            <a href="#" @click.prevent="showPrivacy">隐私政策</a>
          </div>
          <div class="link-group">
            <h4>帮助中心</h4>
            <a href="#" @click.prevent="showHelp('buy')">如何购买</a>
            <a href="#" @click.prevent="showHelp('sell')">如何出售</a>
            <a href="#" @click.prevent="showHelp('faq')">常见问题</a>
          </div>
          <div class="link-group">
            <h4>联系方式</h4>
            <p class="contact-info">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                <polyline points="22,6 12,13 2,6"/>
              </svg>
              support@bookstore.com
            </p>
            <p class="contact-info">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
              </svg>
              400-123-4567
            </p>
          </div>
        </div>
      </div>
      <div class="footer-bottom">
        <p>© 2024 二手书市场. 保留所有权利.</p>
      </div>
    </div>

    <!-- 弹窗 -->
    <div v-if="modalVisible" class="modal-overlay" @click="modalVisible = false">
      <div class="modal-content" @click.stop>
        <button class="modal-close" @click="modalVisible = false">&times;</button>
        <h3>{{ modalTitle }}</h3>
        <div v-html="modalContent"></div>
      </div>
    </div>
  </footer>
</template>

<script setup>
import { ref } from 'vue'

const modalVisible = ref(false)
const modalTitle = ref('')
const modalContent = ref('')

const showAbout = () => {
  modalTitle.value = '平台介绍'
  modalContent.value = `
    <p>二手书市场是一个专注于二手书籍交易的在线平台，致力于让每一本好书找到新的主人。</p>
    <p>我们的愿景是打造一个绿色、便捷、可信赖的二手书交易社区，让知识得以传递，让阅读更加普及。</p>
    <h4>平台特色：</h4>
    <ul>
      <li>海量书籍资源，涵盖各类学科</li>
      <li>安全便捷的交易流程</li>
      <li>专业的书籍品质保障</li>
      <li>贴心的客户服务</li>
    </ul>
  `
  modalVisible.value = true
}

const showContact = () => {
  modalTitle.value = '联系我们'
  modalContent.value = `
    <p>如有任何问题或建议，欢迎通过以下方式联系我们：</p>
    <p><strong>客服邮箱：</strong>support@bookstore.com</p>
    <p><strong>客服电话：</strong>400-123-4567</p>
    <p><strong>工作时间：</strong>周一至周五 9:00-18:00</p>
    <p><strong>公司地址：</strong>北京市海淀区中关村大街1号</p>
  `
  modalVisible.value = true
}

const showPrivacy = () => {
  modalTitle.value = '隐私政策'
  modalContent.value = `
    <p>我们非常重视您的隐私保护，承诺按照相关法律法规保护您的个人信息安全。</p>
    <h4>信息收集：</h4>
    <p>我们仅收集提供服务所必需的信息，包括注册信息、交易信息等。</p>
    <h4>信息使用：</h4>
    <p>您的信息仅用于提供服务、改善用户体验，不会出售给第三方。</p>
    <h4>信息保护：</h4>
    <p>我们采用业界标准的安全技术保护您的信息安全。</p>
  `
  modalVisible.value = true
}

const showHelp = (type) => {
  if (type === 'buy') {
    modalTitle.value = '如何购买'
    modalContent.value = `
      <ol>
        <li>浏览书籍：在首页或书籍列表页面浏览感兴趣的书籍</li>
        <li>查看详情：点击书籍查看详细信息、价格和卖家信息</li>
        <li>加入购物车：选择心仪的书籍加入购物车</li>
        <li>确认订单：填写收货地址，确认订单信息</li>
        <li>完成支付：选择支付方式完成付款</li>
        <li>等待收货：卖家发货后等待快递送达</li>
      </ol>
    `
  } else if (type === 'sell') {
    modalTitle.value = '如何出售'
    modalContent.value = `
      <ol>
        <li>注册登录：首先注册并登录您的账号</li>
        <li>发布书籍：点击"发布书籍"填写书籍信息</li>
        <li>上传图片：拍摄清晰的书籍照片上传</li>
        <li>设置价格：根据书籍品相合理定价</li>
        <li>等待买家：书籍上架后等待买家下单</li>
        <li>发货：收到订单后及时发货</li>
      </ol>
    `
  } else {
    modalTitle.value = '常见问题'
    modalContent.value = `
      <h4>Q: 如何保证书籍质量？</h4>
      <p>A: 我们要求卖家如实描述书籍品相，买家收货后如有问题可申请退款。</p>
      <h4>Q: 支持哪些支付方式？</h4>
      <p>A: 目前支持支付宝、微信支付等主流支付方式。</p>
      <h4>Q: 多久能收到书籍？</h4>
      <p>A: 一般3-7个工作日内送达，具体以物流信息为准。</p>
      <h4>Q: 如何申请退款？</h4>
      <p>A: 在订单详情页面点击"申请退款"，填写退款原因即可。</p>
    `
  }
  modalVisible.value = true
}

</script>

<style scoped>
.footer {
  background: var(--gray-900);
  color: white;
  padding: 64px 0 24px;
  margin-top: 80px;
}

.footer-content {
  display: flex;
  justify-content: space-between;
  padding-bottom: 48px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.footer-brand .logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 12px;
}

.footer-brand > p {
  color: var(--gray-400);
  font-size: 14px;
  margin-bottom: 24px;
}

.footer-links {
  display: flex;
  gap: 60px;
}

.link-group h4 {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--gray-300);
}

.link-group a {
  display: block;
  color: var(--gray-400);
  font-size: 14px;
  margin-bottom: 10px;
  transition: color 0.2s;
  cursor: pointer;
}

.link-group a:hover {
  color: white;
}

.contact-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--gray-400);
  font-size: 14px;
  margin-bottom: 10px;
}

.contact-info svg {
  flex-shrink: 0;
}

.footer-bottom {
  padding-top: 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--gray-500);
  font-size: 14px;
}

/* 弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  color: var(--gray-900);
  padding: 32px;
  border-radius: 12px;
  max-width: 500px;
  max-height: 80vh;
  overflow-y: auto;
  position: relative;
}

.modal-close {
  position: absolute;
  top: 12px;
  right: 16px;
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--gray-500);
}

.modal-close:hover {
  color: var(--gray-900);
}

.modal-content h3 {
  margin-bottom: 16px;
  font-size: 20px;
}

.modal-content h4 {
  margin: 16px 0 8px;
  font-size: 16px;
}

.modal-content p {
  margin-bottom: 12px;
  line-height: 1.6;
}

.modal-content ul, .modal-content ol {
  padding-left: 20px;
  margin-bottom: 12px;
}

.modal-content li {
  margin-bottom: 8px;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .footer-content {
    flex-direction: column;
    gap: 32px;
  }
  
  .footer-links {
    flex-wrap: wrap;
    gap: 32px;
  }
  
  .footer-bottom {
    flex-direction: column;
    gap: 16px;
    text-align: center;
  }
}
</style>
