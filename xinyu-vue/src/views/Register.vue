<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()

const username = ref('')
const password = ref('')
const nickname = ref('')

function register() {
  request.post('/user/register', {
    username: username.value,
    password: password.value,
    nickname: nickname.value
  })
    .then(res => {
      console.log('注册返回结果：', res.data)

      if (res.data.code === 200) {
        alert(res.data.message)
        router.push('/')
      } else {
        alert(res.data.message)
      }
    })
    .catch(err => {
      console.log('注册失败：', err)
      alert('注册失败，请检查后端是否启动')
    })
}

function goLogin() {
  router.push('/')
}
</script>

<template>
  <div class="page">
    <div class="card">
      <h1>创建账号</h1>
      <p class="subtitle">开启你的心情记录之旅</p>

      <div class="form">
        <input v-model="username" placeholder="请输入用户名" />
        <input v-model="password" type="password" placeholder="请输入密码" />
        <input v-model="nickname" placeholder="请输入昵称" />

        <button class="primary-btn" @click="register">注册</button>
        <button class="text-btn" @click="goLogin">已有账号？返回登录</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
}

.card {
  width: 380px;
  padding: 36px 32px;
  background: #ffffff;
  border-radius: 18px;
  box-shadow: 0 12px 30px rgba(80, 100, 140, 0.15);
  text-align: center;
}

h1 {
  margin: 0;
  font-size: 30px;
  color: #4b6cb7;
}

.subtitle {
  margin: 10px 0 28px;
  color: #777;
  font-size: 15px;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

input {
  height: 42px;
  padding: 0 14px;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
}

input:focus {
  border-color: #6c8cff;
}

.primary-btn {
  height: 42px;
  border: none;
  border-radius: 10px;
  background: #6c8cff;
  color: white;
  font-size: 15px;
}

.primary-btn:hover {
  background: #5878f0;
}

.text-btn {
  border: none;
  background: transparent;
  color: #6c8cff;
  font-size: 14px;
}
</style>