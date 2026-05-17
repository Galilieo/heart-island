<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import logoUrl from '../assets/logo-xinyu.png'

const router = useRouter()

const username = ref('')
const password = ref('')
const nickname = ref('')
const message = ref('')
const messageType = ref('error')
const loading = ref(false)

function register() {
  if (loading.value) {
    return
  }

  if (!username.value.trim()) {
    showMessage('请输入用户名', 'warning')
    return
  }

  if (!password.value.trim()) {
    showMessage('请输入密码', 'warning')
    return
  }

  if (!nickname.value.trim()) {
    showMessage('请输入昵称', 'warning')
    return
  }

  loading.value = true

  request
    .post('/user/register', {
      username: username.value.trim(),
      password: password.value,
      nickname: nickname.value.trim()
    })
    .then((res) => {
      console.log('注册返回结果：', res.data)

      if (res.data.code === 200) {
        showMessage(res.data.message || '注册成功', 'success')
        router.push('/')
      } else {
        showMessage(res.data.message || '注册失败', 'error')
      }
    })
    .catch((err) => {
      console.log('注册失败：', err)
      showMessage('注册失败，请检查后端是否启动', 'error')
    })
    .finally(() => {
      loading.value = false
    })
}

function goLogin() {
  router.push('/')
}

function showMessage(text, type = 'error') {
  message.value = text
  messageType.value = type
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-card">
      <div class="brand-mark">
        <img :src="logoUrl" alt="心屿 logo" />
      </div>
      <p class="kicker">新的开始</p>
      <h1>创建账号</h1>
      <p class="subtitle">给自己的心情留一个安静的位置，从今天开始慢慢记录。</p>

      <div v-if="message" class="message" :class="messageType">
        {{ message }}
      </div>

      <div class="form">
        <input v-model="username" placeholder="请输入用户名" />
        <input v-model="password" type="password" placeholder="请输入密码" />
        <input v-model="nickname" placeholder="请输入昵称" />

        <button class="primary-btn" :disabled="loading" @click="register">
          {{ loading ? '注册中' : '注册' }}
        </button>
        <button class="text-btn" @click="goLogin">已有账号？返回登录</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 28px;
  color: #2f3a3d;
  background:
    radial-gradient(circle at 14% 18%, rgba(95, 158, 160, 0.2), transparent 28%),
    radial-gradient(circle at 86% 12%, rgba(238, 234, 248, 0.82), transparent 28%),
    radial-gradient(circle at 76% 86%, rgba(216, 169, 109, 0.12), transparent 26%),
    linear-gradient(135deg, #faf8f3 0%, #f6f3ea 48%, #fffdf8 100%);
  background-size: 120% 120%;
  animation: ambientShift 20s ease-in-out infinite alternate;
}

.auth-card {
  width: 380px;
  padding: 34px 32px;
  border: 1px solid rgba(90, 110, 150, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 18px 40px rgba(46, 64, 100, 0.08);
  text-align: center;
}

.brand-mark {
  width: 48px;
  height: 48px;
  margin: 0 auto 18px;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 12px 28px rgba(95, 158, 160, 0.24);
}

.brand-mark img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.kicker {
  margin: 0 0 8px;
  color: #3f7378;
  font-size: 13px;
  font-weight: 700;
}

h1 {
  margin: 0;
  font-size: 30px;
  color: #2f3a3d;
  letter-spacing: 0;
}

.subtitle {
  margin: 10px 0 24px;
  color: #778184;
  font-size: 15px;
  line-height: 1.7;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

input {
  height: 42px;
  padding: 0 14px;
  border: 1px solid #e6e1d8;
  border-radius: 8px;
  background: #fff;
  color: #1f2937;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

input:focus {
  border-color: #5f9ea0;
  box-shadow: 0 0 0 3px rgba(95, 158, 160, 0.14);
}

.primary-btn {
  height: 42px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #4f8f91, #6caeb0);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  box-shadow: 0 10px 22px rgba(95, 158, 160, 0.2);
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease;
}

.primary-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #3f7378, #5f9ea0);
  transform: translateY(-1px);
}

.primary-btn:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.text-btn {
  border: none;
  background: transparent;
  color: #3f7378;
  font-size: 14px;
  cursor: pointer;
}

.text-btn:hover {
  color: #2f5f63;
}

.message {
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  text-align: left;
}

.message.success {
  background: #e6f5ed;
  color: #28724d;
}

.message.error {
  background: #fff1f1;
  color: #c95151;
}

.message.warning {
  background: #fff4dc;
  color: #946200;
}

@media (max-width: 520px) {
  .auth-page {
    padding: 18px;
  }

  .auth-card {
    width: 100%;
    padding: 30px 22px;
  }
}

@keyframes ambientShift {
  0% {
    background-position: 0% 0%;
  }

  100% {
    background-position: 100% 70%;
  }
}
</style>
