import { createApp } from 'vue';
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

import App from './App.vue';
import router from './router';
import { IdScannerNative } from './scanner/IdScannerNative';

const app = createApp(App).use(IonicVue).use(router);

// Mount the app immediately. The Capacitor + Android WebView combination
// occasionally leaves <ion-router-outlet> empty even after vue-router is ready,
// so App.vue does not rely on ionic-vue-router to switch between the home
// and result views — it swaps them with v-if based on a reactive flag.
app.mount('#app');

// Touch the in-app plugin once so the Capacitor bridge finishes its handshake
// before the camera button is tapped. The promise is intentionally not awaited.
IdScannerNative.initLicense().catch(() => {
  /* The plugin has no web fallback; this is expected outside the native shell. */
});
