<script setup lang="ts">
</script>

<template>
  <nav class="nav">
    <RouterLink to="/">
      Weather
    </RouterLink>
    <RouterLink to="/about">
      About
    </RouterLink>
    <RouterLink to="/queue">
      Queue
    </RouterLink>
  </nav>
  <main class="main">
    <RouterView />
  </main>
</template>

<style>
body {
  font-family: system-ui, sans-serif;
  margin: 0;
  padding: 0 1rem;
}

.nav {
  display: flex;
  gap: 1rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid #ddd;
}

.nav a.router-link-active {
  font-weight: bold;
}
</style>