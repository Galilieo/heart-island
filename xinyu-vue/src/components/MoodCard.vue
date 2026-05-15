<template>
  <div class="card">
    <h3>{{ mood }}</h3>
    <p>{{ content }}</p>

    <button @click="deleteSelf">删除</button>
  </div>
</template>

<script setup>
const props = defineProps({
  id: Number,
  mood: String,
  content: String
})

const emit = defineEmits(['deleteRecord'])

function deleteSelf() {
  emit('deleteRecord', props.id)
}
</script>

<style scoped>
.card {
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
}

button {
  margin-top: 8px;
}
</style>