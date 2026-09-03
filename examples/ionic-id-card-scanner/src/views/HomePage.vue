<template>
  <ion-page>
    <ion-header :translucent="true">
      <ion-toolbar>
        <ion-title>ID Card Scanner</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <ion-grid class="ion-padding">
        <ion-row>
          <ion-col size="12">
            <ion-card>
              <ion-card-header>
                <ion-card-title>Passport &amp; ID Card MRZ Scanner</ion-card-title>
                <ion-card-subtitle>
                  Dynamsoft MRZ Scanner native SDK · Android &amp; iOS
                </ion-card-subtitle>
              </ion-card-header>
              <ion-card-content>
                <p>
                  Point the camera at the Machine-Readable Zone (MRZ) of a passport,
                  ID card or visa, or pick a document photo from the gallery. The scan
                  runs entirely on the device with the native Dynamsoft SDK.
                </p>
              </ion-card-content>
            </ion-card>
          </ion-col>
        </ion-row>

        <!-- Data source 1: live camera -->
        <ion-row>
          <ion-col size="12">
            <ion-card>
              <ion-card-header>
                <ion-card-title>Camera</ion-card-title>
                <ion-card-subtitle>Live detection with real-time preview</ion-card-subtitle>
              </ion-card-header>
              <ion-card-content>
                <ion-button expand="block" color="primary" :disabled="scanning" @click="scanWithCamera">
                  <ion-icon slot="start" :icon="videocam" />
                  Scan with Camera
                </ion-button>
              </ion-card-content>
            </ion-card>
          </ion-col>
        </ion-row>

        <!-- Data source 2: file / gallery image -->
        <ion-row>
          <ion-col size="12">
            <ion-card>
              <ion-card-header>
                <ion-card-title>File</ion-card-title>
                <ion-card-subtitle>Process a document image</ion-card-subtitle>
              </ion-card-header>
              <ion-card-content>
                <ion-button expand="block" color="secondary" :disabled="scanning" @click="scanFromFile">
                  <ion-icon slot="start" :icon="image" />
                  Choose an Image
                </ion-button>
              </ion-card-content>
            </ion-card>
          </ion-col>
        </ion-row>

        <ion-row v-if="error">
          <ion-col size="12">
            <ion-text color="danger">
              <p>{{ error }}</p>
            </ion-text>
          </ion-col>
        </ion-row>

        <ion-row v-if="scanning">
          <ion-col size="12" class="ion-text-center">
            <ion-spinner name="crescent" />
            <p>Waiting for the native scanner…</p>
          </ion-col>
        </ion-row>
      </ion-grid>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonGrid, IonRow, IonCol, IonCard, IonCardHeader, IonCardTitle, IonCardSubtitle, IonCardContent, IonButton, IonIcon, IonText, IonSpinner } from '@ionic/vue';
import { videocam, image } from 'ionicons/icons';
import { IdScannerNative } from '../scanner/IdScannerNative';
import { scanStore } from '../scanner/store';

const emit = defineEmits<{ (e: 'scanned'): void }>();
const scanning = ref(false);
const error = ref('');

async function ensureLicense(): Promise<void> {
  const res = await IdScannerNative.initLicense();
  if (!res.success) {
    throw new Error(res.message);
  }
}

async function scanWithCamera() {
  scanning.value = true;
  error.value = '';
  try {
    await ensureLicense();
    const result = await IdScannerNative.startScan();
    scanStore.result = result;
    emit('scanned');
  } catch (e: any) {
    if (e?.message !== 'canceled') {
      error.value = e?.message ?? String(e);
    }
  } finally {
    scanning.value = false;
  }
}

async function scanFromFile() {
  scanning.value = true;
  error.value = '';
  try {
    await ensureLicense();
    const result = await IdScannerNative.scanFromGallery();
    scanStore.result = result;
    emit('scanned');
  } catch (e: any) {
    if (e?.message !== 'canceled') {
      error.value = e?.message ?? String(e);
    }
  } finally {
    scanning.value = false;
  }
}
</script>
