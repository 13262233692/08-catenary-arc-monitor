<template>
  <div class="login-page">
    <div class="circuit-bg">
      <div class="line line-1"></div>
      <div class="line line-2"></div>
      <div class="line line-3"></div>
      <div class="line line-4"></div>
      <div class="line line-5"></div>
      <div class="line line-6"></div>
      <div class="spark spark-1"></div>
      <div class="spark spark-2"></div>
      <div class="spark spark-3"></div>
      <div class="spark spark-4"></div>
    </div>
    <div class="login-card">
      <div class="login-header">
        <el-icon class="train-icon" :size="36"><Van /></el-icon>
        <h1 class="login-title">弓网电弧在线监测中台</h1>
        <p class="login-subtitle">Catenary Arc Online Monitoring Platform</p>
      </div>
      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.login(loginForm)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0d0f1a 0%, #141728 40%, #1a1d3a 70%, #0d0f1a 100%);
  position: relative;
  overflow: hidden;
}

.circuit-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  opacity: 0.15;
}

.line {
  position: absolute;
  background: linear-gradient(90deg, transparent, #409eff, transparent);
  height: 1px;
}

.line-1 {
  top: 20%;
  left: -10%;
  width: 120%;
  transform: rotate(-5deg);
  animation: line-flow 8s linear infinite;
}

.line-2 {
  top: 40%;
  left: -10%;
  width: 120%;
  transform: rotate(3deg);
  animation: line-flow 12s linear infinite reverse;
}

.line-3 {
  top: 60%;
  left: -10%;
  width: 120%;
  transform: rotate(-2deg);
  animation: line-flow 10s linear infinite;
}

.line-4 {
  top: 35%;
  left: -10%;
  width: 120%;
  transform: rotate(8deg);
  animation: line-flow 15s linear infinite reverse;
  background: linear-gradient(90deg, transparent, #e6a23c, transparent);
}

.line-5 {
  top: 75%;
  left: -10%;
  width: 120%;
  transform: rotate(-6deg);
  animation: line-flow 9s linear infinite;
  background: linear-gradient(90deg, transparent, #67c23a, transparent);
}

.line-6 {
  top: 10%;
  left: -10%;
  width: 120%;
  transform: rotate(4deg);
  animation: line-flow 11s linear infinite reverse;
}

.spark {
  position: absolute;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #409eff;
  box-shadow: 0 0 8px 2px rgba(64, 158, 255, 0.6);
}

.spark-1 {
  top: 20%;
  animation: spark-move-1 8s linear infinite;
}

.spark-2 {
  top: 40%;
  animation: spark-move-2 12s linear infinite reverse;
}

.spark-3 {
  top: 60%;
  animation: spark-move-3 10s linear infinite;
}

.spark-4 {
  top: 35%;
  background: #e6a23c;
  box-shadow: 0 0 8px 2px rgba(230, 162, 60, 0.6);
  animation: spark-move-4 15s linear infinite reverse;
}

@keyframes line-flow {
  0% { opacity: 0.3; }
  50% { opacity: 0.8; }
  100% { opacity: 0.3; }
}

@keyframes spark-move-1 {
  0% { left: -2%; }
  100% { left: 102%; }
}

@keyframes spark-move-2 {
  0% { left: -2%; }
  100% { left: 102%; }
}

@keyframes spark-move-3 {
  0% { left: -2%; }
  100% { left: 102%; }
}

@keyframes spark-move-4 {
  0% { left: -2%; }
  100% { left: 102%; }
}

.login-card {
  width: 420px;
  background: rgba(26, 29, 46, 0.95);
  border: 1px solid #2a2d42;
  border-radius: 16px;
  padding: 48px 40px 36px;
  backdrop-filter: blur(20px);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.train-icon {
  color: #409eff;
  margin-bottom: 16px;
  filter: drop-shadow(0 0 8px rgba(64, 158, 255, 0.4));
}

.login-title {
  font-size: 24px;
  font-weight: 700;
  color: #e0e3eb;
  margin: 0 0 8px;
  letter-spacing: 2px;
}

.login-subtitle {
  font-size: 12px;
  color: #5a5f7a;
  margin: 0;
  letter-spacing: 1px;
}

.login-form {
  margin-top: 8px;
}

.login-form :deep(.el-input__wrapper) {
  background: #141728;
  border: 1px solid #2a2d42;
  box-shadow: none;
  border-radius: 8px;
}

.login-form :deep(.el-input__wrapper:hover),
.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: #409eff;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.2);
}

.login-form :deep(.el-input__inner) {
  color: #e0e3eb;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: #5a5f7a;
}

.login-form :deep(.el-input__prefix .el-icon) {
  color: #5a5f7a;
}

.login-form :deep(.el-form-item__error) {
  color: #f56c6c;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff, #337ecc);
  border: none;
}

.login-btn:hover {
  background: linear-gradient(135deg, #66b1ff, #409eff);
}
</style>
