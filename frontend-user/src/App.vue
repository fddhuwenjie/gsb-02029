<template>
  <div class="app">
    <Header />
    <main class="main">
      <router-view />
    </main>
    <Footer />
    <Toast 
      :message="toastState.message" 
      :type="toastState.type" 
      v-model:show="toastState.show" 
    />
  </div>
</template>

<script setup>
import Header from './components/Header.vue'
import Footer from './components/Footer.vue'
import Toast from './components/Toast.vue'
import { useToast } from './composables/useToast'

const { toastState } = useToast()
</script>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main {
  flex: 1;
  padding-top: 80px;
}
</style>
