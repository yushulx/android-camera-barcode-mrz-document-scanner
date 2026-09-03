<template>
  <ion-page>
    <ion-header :translucent="true">
      <ion-toolbar>
        <ion-title>QR Code Scanner</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <ion-header collapse="condense">
        <ion-toolbar>
          <ion-title size="large">QR Code Scanner</ion-title>
        </ion-toolbar>
      </ion-header>

      <div class="page">
        <p class="intro">
          Decode QR codes and 1D/2D barcodes with the Dynamsoft Capture Vision native SDK. Pick a
          data source: the live camera or a still image from the gallery.
        </p>

        <ion-segment v-model="source" @ionChange="onSourceChange">
          <ion-segment-button value="camera">
            <ion-label>Camera</ion-label>
          </ion-segment-button>
          <ion-segment-button value="file">
            <ion-label>Image File</ion-label>
          </ion-segment-button>
        </ion-segment>

        <div class="actions">
          <ion-button expand="block" size="large" :disabled="busy" @click="scan">
            <ion-icon slot="start" :icon="source === 'camera' ? cameraOutline : imagesOutline" />
            {{ source === 'camera' ? 'Open Camera Scanner' : 'Pick Image and Scan' }}
          </ion-button>
        </div>

        <ion-card v-if="errorMessage">
          <ion-card-content class="error">{{ errorMessage }}</ion-card-content>
        </ion-card>

        <div class="result-head" v-if="results.length">
          <ion-note>{{ results.length }} barcode(s) decoded</ion-note>
          <ion-button fill="clear" size="small" @click="clear">Clear</ion-button>
        </div>

        <ion-list v-if="results.length">
          <ion-item v-for="(item, index) in results" :key="index">
            <ion-label class="ion-text-wrap">
              <h3>{{ index + 1 }}. {{ item.formatString }}</h3>
              <p class="barcode-text">{{ item.text }}</p>
              <ion-note v-if="item.points && item.points.length === 4">
                corners: {{ formatPoints(item.points) }}
              </ion-note>
            </ion-label>
          </ion-item>
        </ion-list>

        <div class="empty" v-else-if="!busy && scanned">
          <ion-icon :icon="scanOutline" />
          <p>No barcode found. Try a sharper or larger image.</p>
        </div>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonNote,
  IonPage,
  IonSegment,
  IonSegmentButton,
  IonTitle,
  IonToolbar,
} from '@ionic/vue';
import { cameraOutline, imagesOutline, scanOutline } from 'ionicons/icons';
import {
  BarcodeScannerNative,
  type BarcodeResult,
} from '../scanner/BarcodeScannerNative';

const source = ref<'camera' | 'file'>('camera');
const results = ref<BarcodeResult[]>([]);
const busy = ref(false);
const scanned = ref(false);
const errorMessage = ref('');

function onSourceChange() {
  errorMessage.value = '';
}

function clear() {
  results.value = [];
  scanned.value = false;
  errorMessage.value = '';
}

function formatPoints(points: Array<{ x: number; y: number }>): string {
  return points.map((p) => `(${Math.round(p.x)},${Math.round(p.y)})`).join(' ');
}

function unwrap(err: unknown): string {
  if (typeof err === 'string') return err;
  if (err && typeof err === 'object') {
    const e = err as { message?: string; errorMessage?: string };
    if (e.message) return e.message;
    if (e.errorMessage) return e.errorMessage;
  }
  return 'Unknown error';
}

async function scan() {
  busy.value = true;
  errorMessage.value = '';
  scanned.value = false;
  try {
    let payload;
    if (source.value === 'camera') {
      payload = await BarcodeScannerNative.startScan();
    } else {
      payload = await BarcodeScannerNative.scanFromGallery();
    }
    results.value = payload.results ?? [];
    scanned.value = true;
  } catch (e) {
    const message = unwrap(e);
    // The native scanner rejects with "canceled" when the user backs out.
    if (!/cancel/i.test(message)) {
      errorMessage.value = message;
    }
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.page {
  padding: 16px;
}

.intro {
  color: var(--ion-color-medium, #6b7280);
  font-size: 14px;
  line-height: 1.5;
  margin: 0 0 16px;
}

.actions {
  margin-top: 20px;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24px;
  padding: 0 4px;
}

.barcode-text {
  white-space: pre-wrap;
  word-break: break-all;
}

.error {
  color: #d92d20;
  white-space: pre-wrap;
  word-break: break-word;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 48px;
  color: var(--ion-color-medium, #6b7280);
  text-align: center;
}

.empty ion-icon {
  font-size: 48px;
}
</style>
