<script setup>
// 根组件。<router-view/> 渲染当前路由对应的页面，
// Toast / ConfirmDialog 是全局浮层（一份就够），需要在这里挂在最外层。
import Toast from './components/ui/Toast.vue'
import ConfirmDialog from './components/ui/ConfirmDialog.vue'
</script>

<template>
  <RouterView v-slot="{ Component, route }">
    <Transition name="route-fade" mode="out-in">
      <component :is="Component" :key="route.fullPath" />
    </Transition>
  </RouterView>
  <Toast />
  <ConfirmDialog />
</template>
