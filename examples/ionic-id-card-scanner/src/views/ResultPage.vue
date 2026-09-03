<template>
  <ion-page>
    <ion-header :translucent="true">
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-button fill="clear" @click="goBack">
            <ion-icon slot="icon-only" :icon="arrowBack" />
          </ion-button>
        </ion-buttons>
        <ion-title>Scan Result</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <ion-grid class="ion-padding" v-if="result">
        <!-- Deskewed document image (document boundary detection + perspective correction) -->
        <ion-row v-if="result.documentImageBase64">
          <ion-col size="12">
            <ion-card>
              <ion-card-header>
                <ion-card-title>Detected document</ion-card-title>
                <ion-card-subtitle>Card boundary detected &amp; perspective corrected on-device</ion-card-subtitle>
              </ion-card-header>
              <ion-card-content>
                <div class="media-frame media-frame--doc">
                  <img
                    :src="result.documentImageBase64"
                    alt="Deskewed document"
                    class="result-image" />
                </div>
              </ion-card-content>
            </ion-card>
          </ion-col>
        </ion-row>

        <!-- Portrait -->
        <ion-row v-if="result.portraitBase64">
          <ion-col size="12">
            <ion-card>
              <ion-card-header>
                <ion-card-title>Detected portrait</ion-card-title>
                <ion-card-subtitle>Extracted from the auxiliary portrait zone</ion-card-subtitle>
              </ion-card-header>
              <ion-card-content>
                <div class="media-frame media-frame--portrait">
                  <img
                    :src="result.portraitBase64"
                    alt="Detected portrait"
                    class="result-image" />
                </div>
              </ion-card-content>
            </ion-card>
          </ion-col>
        </ion-row>

        <!-- Parsed MRZ fields -->
        <ion-row>
          <ion-col size="12">
            <ion-card>
              <ion-card-header>
                <ion-card-title>
                  <template v-if="result.fields">{{ result.fields.documentType }}</template>
                  <ion-badge color="primary" v-if="result.fields?.documentNumber">
                    {{ result.fields.documentNumber }}
                  </ion-badge>
                </ion-card-title>
              </ion-card-header>
              <ion-card-content>
                <ion-list :inset="true" v-if="fieldRows && Object.keys(fieldRows).length > 0">
                  <ion-item v-for="(value, key) in fieldRows" :key="key">
                    <ion-label>
                      <h3>{{ displayName(key) }}</h3>
                      <p>{{ value }}</p>
                    </ion-label>
                  </ion-item>
                </ion-list>
                <p v-else class="empty-hint">No fields were parsed. Please scan again.</p>
              </ion-card-content>
            </ion-card>
          </ion-col>
        </ion-row>

        <!-- Actions -->
        <ion-row>
          <ion-col size="12">
            <ion-button expand="block" color="primary" @click="goBack">
              Rescan
            </ion-button>
          </ion-col>
        </ion-row>
      </ion-grid>

      <ion-grid class="ion-padding" v-else>
        <ion-row>
          <ion-col size="12" class="ion-text-center ion-padding">
            <p>No scan result available.</p>
            <ion-button expand="block" color="medium" @click="goBack">Back</ion-button>
          </ion-col>
        </ion-row>
      </ion-grid>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { IonPage, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton, IonIcon, IonContent, IonGrid, IonRow, IonCol, IonImg, IonCard, IonCardHeader, IonCardTitle, IonCardSubtitle, IonCardContent, IonList, IonItem, IonLabel, IonBadge } from '@ionic/vue';
import { arrowBack } from 'ionicons/icons';
import { scanStore } from '../scanner/store';

const emit = defineEmits<{ (e: 'back'): void }>();

const result = computed(() => scanStore.result);

function goBack() {
  emit('back');
}

const fieldRows = computed(() => {
  const f = result.value?.fields;
  if (!f) return {};
  const { documentType: _dt, ...rest } = f;
  return rest;
});

const NAME_MAP: Record<string, string> = {
  name: 'Name',
  sex: 'Sex',
  age: 'Age',
  documentNumber: 'Document Number',
  issuingState: 'Issuing State',
  nationality: 'Nationality',
  dateOfBirth: 'Date of Birth',
  dateOfExpiry: 'Date of Expiry',
};

function displayName(key: string): string {
  return NAME_MAP[key] ?? key;
}
</script>

<style scoped>
.media-frame {
  width: 100%;
  background: #f5f6f8;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  box-sizing: border-box;
  overflow: hidden;
}

.media-frame--doc {
  aspect-ratio: 85.6 / 53.98; /* ID-1 / credit-card aspect ratio */
  max-height: 320px;
  width: 100%;
}

.media-frame--portrait {
  aspect-ratio: 3 / 4;
  max-height: 280px;
  width: auto;
  max-width: 240px;
  margin: 0 auto;
}

/* The native SDK returns full-size JPEGs. The wrapper caps the height, the
 * image uses object-fit: contain so the whole picture is always visible
 * (never cropped) regardless of the card / portrait aspect ratio. */
.result-image {
  display: block;
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 6px;
}

.empty-hint {
  color: var(--ion-color-medium);
}
</style>
