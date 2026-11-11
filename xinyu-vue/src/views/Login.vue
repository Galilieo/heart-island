<script setup>
// 登录页。表单 → userStore.login → 成功跳 /home。
// 校验仅做"非空"，详细错误（密码错等）由后端返回，通过 toast 展示。
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthLayout from '../components/layout/AuthLayout.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import { useUserStore } from '../stores/user'
import { useToast } from '../composables/useToast'
import { useAsyncAction } from '../composables/useAsyncAction'

const router = useRouter()
const userStore = useUserStore()
const toast = useToast()

const username = ref('')
const password = ref('')

const { run: submit, loading } = useAsyncAction(
  () => userStore.login({ username: username.value.trim(), password: password.value }),
  { errorMsg: '登录失败' }
)

async function handleLogin() {
  if (!username.value.trim()) {
    toast.warn('请输入用户名')
    return
  }
  if (!password.value.trim()) {
    toast.warn('请输入密码')
    return
  }
  const res = await submit()
  if (res.ok) {
    toast.success(res.message || '登录成功')
    router.push('/home')
  }
}

function goRegister() {
  router.push('/register')
}
</script>

<template>
  <AuthLayout
    kicker="欢迎回来"
    title="心屿"
    subtitle="记记今天的心情，也看看大家的近况。"
    slogan="今天过得怎么样？"
  >
    <BaseInput
      v-model="username"
      label="用户名"
      placeholder="请输入用户名"
      size="lg"
      @enter="handleLogin"
    />
    <BaseInput
      v-model="password"
      type="password"
      label="密码"
      placeholder="请输入密码"
      size="lg"
      @enter="handleLogin"
    />
    <BaseButton
      variant="primary"
      size="lg"
      :loading="loading"
      block
      @click="handleLogin"
    >登录</BaseButton>

    <RouterLink to="/privacy">隐私政策</RouterLink>

    <template #footer>
      <BaseButton variant="link" @click="goRegister">
        还没有账号？去注册 →
      </BaseButton>
    </template>
  </AuthLayout>
</template>
