<template>
  <Teleport to="body">
    <Transition name="toast">
      <div v-if="visible" class="toast-container">
        <div :class="['toast', type]">
          <div class="toast-icon">
            <svg v-if="type === 'success'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
              <polyline points="22 4 12 14.01 9 11.01"></polyline>
            </svg>
            <svg v-else-if="type === 'error'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="15" y1="9" x2="9" y2="15"></line>
              <line x1="9" y1="9" x2="15" y2="15"></line>
            </svg>
            <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="16" x2="12" y2="12"></line>
              <line x1="12" y1="8" x2="12.01" y2="8"></line>
            </svg>
          </div>
          <span class="toast-message">{{ message }}</span>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  message: { type: String, default: '' },
  type: { type: String, default: 'info' },
  duration: { type: Number, default: 3000 },
  show: { type: Boolean, default: false }
})

const emit = defineEmits(['update:show'])
const visible = ref(false)

watch(() => props.show, (val) => {
  visible.value = val
  if (val && props.duration > 0) {
    setTimeout(() => {
      visible.value = false
      emit('update:show', false)
    }, props.duration)
  }
})
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 100px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
}

.toast {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  border-radius: 12px;
  background: white;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  font-weight: 500;
}

.toast.success {
  background: linear-gradient(135deg, #d1fae5, #a7f3d0);
  color: #065f46;
}

.toast.error {
  background: linear-gradient(135deg, #fee2e2, #fecaca);
  color: #991b1b;
}

.toast.info {
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  color: #1e40af;
}

.toast.warning {
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  color: #92400e;
}

.toast-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(-50%) translateY(-20px);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-20px);
}
</style>
