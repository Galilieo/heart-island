<script setup>
import Toast from './components/ui/Toast.vue'
import ConfirmDialog from './components/ui/ConfirmDialog.vue'
</script>

<template>
  <router-view />
  <Toast />
  <ConfirmDialog />
</template>
