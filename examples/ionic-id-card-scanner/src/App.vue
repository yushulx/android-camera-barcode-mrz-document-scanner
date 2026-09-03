<template>
  <ion-app>
    <HomePage v-if="view === 'home'" @scanned="onScanned" />
    <ResultPage v-else-if="view === 'result'" @back="goHome" />
  </ion-app>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import type { PluginListenerHandle } from '@capacitor/core';
import { App as CapApp } from '@capacitor/app';
import HomePage from './views/HomePage.vue';
import ResultPage from './views/ResultPage.vue';

const view = ref<'home' | 'result'>('home');
let backListener: PluginListenerHandle | undefined;

function onScanned() {
  view.value = 'result';
}

function goHome() {
  view.value = 'home';
}

onMounted(async () => {
  // Android hardware / system back button:
  // - on the result page: go back to the home page instead of quitting
  // - on the home page: perform the default app-exit behaviour
  try {
    backListener = await CapApp.addListener('backButton', () => {
      if (view.value === 'result') {
        view.value = 'home';
      } else {
        // home screen: hand the back press to the OS so the app closes
        void CapApp.exitApp();
      }
    });
  } catch {
    // Running in a plain browser: no hardware back button to handle.
  }
});

onBeforeUnmount(async () => {
  await backListener?.remove();
});
</script>
