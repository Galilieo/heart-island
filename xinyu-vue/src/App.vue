<script setup>
// 根组件。<router-view/> 渲染当前路由对应的页面，
// Toast / ConfirmDialog 是全局浮层（一份就够），需要在这里挂在最外层。
import Toast from './components/ui/Toast.vue'
import ConfirmDialog from './components/ui/ConfirmDialog.vue'
</script>

<template>
  <!-- 嵌套路由（目前只有后台）按父级路径做 key，切换子页面时不重挂布局；
       其他页面仍按完整路径播放路由过渡。 -->
  <RouterView v-slot="{ Component, route }">
    <Transition name="route-fade" mode="out-in">
      <component
        :is="Component"
        :key="route.matched.length > 1 ? route.matched[0].path : route.fullPath"
      />
    </Transition>
  </RouterView>
  <Toast />
  <ConfirmDialog />
</template>
