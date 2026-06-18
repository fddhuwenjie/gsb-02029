import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'Home', component: () => import('../views/Home.vue') },
  { path: '/books', name: 'Books', component: () => import('../views/Books.vue') },
  { path: '/book/:id', name: 'BookDetail', component: () => import('../views/BookDetail.vue') },
  { path: '/cart', name: 'Cart', component: () => import('../views/Cart.vue'), meta: { auth: true } },
  { path: '/orders', name: 'Orders', component: () => import('../views/Orders.vue'), meta: { auth: true } },
  { path: '/publish', name: 'Publish', component: () => import('../views/Publish.vue'), meta: { auth: true } },
  { path: '/profile', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { auth: true } },
  { path: '/login', redirect: '/' },
  { path: '/register', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('user_token')
  if (to.meta.auth && !token) {
    next('/')
    setTimeout(() => {
      if (window.__showLoginModal) window.__showLoginModal()
    }, 100)
  } else {
    next()
  }
})

export default router
