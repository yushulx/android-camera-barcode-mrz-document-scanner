import { createApp } from 'vue';
import App from './App.vue';
import router from './router';

import { IonicVue } from '@ionic/vue';

/* Core CSS required for Ionic components to work properly */
import '@ionic/vue/css/core.css';

/* Basic CSS for apps built with Ionic */
import '@ionic/vue/css/normalize.css';
import '@ionic/vue/css/structure.css';
import '@ionic/vue/css/typography.css';

/* Optional CSS utils that can be commented out */
import '@ionic/vue/css/padding.css';
import '@ionic/vue/css/float-elements.css';
import '@ionic/vue/css/text-alignment.css';
import '@ionic/vue/css/text-transformation.css';
import '@ionic/vue/css/flex-utils.css';
import '@ionic/vue/css/display.css';

/* Theme variables */
import './theme/variables.css';

import { BarcodeScannerNative, LICENSE_KEY } from './scanner/BarcodeScannerNative';

const app = createApp(App).use(IonicVue).use(router);

router.isReady().then(() => {
  app.mount('#app');
});

// Initialize the Dynamsoft license once at startup.
// On the web (`npm run dev`) the plugin has no implementation and the call simply rejects,
// which is fine because scanning on mobile always runs through the native plugin.
BarcodeScannerNative.initLicense({ license: LICENSE_KEY })
  .then((res) => {
    if (!res.success) {
      console.warn('License initialization failed:', res.message);
    }
  })
  .catch((err) => console.log('Native plugin unavailable (web build):', err));
