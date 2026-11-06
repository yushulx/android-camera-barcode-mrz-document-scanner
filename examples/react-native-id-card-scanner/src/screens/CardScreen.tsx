import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  Alert,
  Image,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import {useFocusEffect} from '@react-navigation/native';
import {TextButton} from '../components/TextButton';
import {IDCardManager} from '../services/IDCardManager';
import {CardDraft} from '../services/CardDraft';
import {
  CARD_INFO_FIELDS,
  type CardInfo,
  type CardSide,
} from '../models/ScannedIDCard';
import type {StackScreenProps} from '../App';

interface ImageSectionProps {
  label: string;
  base64: string;
  onPress: () => void;
}

function ImageSection({label, base64, onPress}: ImageSectionProps) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{label}</Text>
      <TouchableOpacity activeOpacity={0.8} onPress={onPress}>
        {base64 ? (
          <Image
            style={styles.cardImage}
            resizeMode="contain"
            source={{uri: `data:image/jpeg;base64,${base64}`}}
          />
        ) : (
          <View style={[styles.cardImage, styles.emptyImage]}>
            <Text style={styles.emptyImageText}>+ Add image</Text>
          </View>
        )}
      </TouchableOpacity>
    </View>
  );
}

export function CardScreen({navigation, route}: StackScreenProps<'Card'>) {
  // The screen can be rebuilt when it regains focus, so its state lives in the
  // draft store instead of in useState alone.
  const [draft, setDraft] = useState(CardDraft.read);
  const loadedKeyRef = useRef('');

  const refresh = useCallback(() => {
    setDraft({...CardDraft.read()});
  }, []);

  // Load the stored record the first time this card is opened.
  useEffect(() => {
    const key = route.params?.cardKey;
    if (!key || loadedKeyRef.current === key) {
      return;
    }
    loadedKeyRef.current = key;
    IDCardManager.load(key).then(card => {
      if (!card) {
        return;
      }
      CardDraft.patch({
        cardKey: key,
        frontImage: card.frontImage,
        backImage: card.backImage,
        info: card.info,
      });
      refresh();
    });
  }, [route.params?.cardKey, refresh]);

  // A capture arrives while the camera screen is on top; read it back on focus.
  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh]),
  );

  const saveCard = async () => {
    const {info, frontImage, backImage} = CardDraft.read();
    const missing = CARD_INFO_FIELDS.filter(field => !info[field].trim());
    if (missing.length > 0) {
      Alert.alert('Card info not complete', `Missing: ${missing.join(', ')}`);
      return;
    }

    const {cardKey} = CardDraft.read();
    const timestamp = cardKey ? Number(cardKey) : Date.now();
    CardDraft.patch({cardKey: String(timestamp)});

    await IDCardManager.save({timestamp, frontImage, backImage, info});
    CardDraft.reset();
    Alert.alert('Saved', 'The card is stored on this device.', [
      {text: 'OK', onPress: () => navigation.popToTop()},
    ]);
  };

  useEffect(() => {
    navigation.setOptions({
      headerRight: () => <TextButton title="Save" onPress={saveCard} />,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigation, draft]);

  const goToCamera = (side: CardSide) => {
    navigation.navigate('Camera', {side});
  };

  const updateField = (field: keyof CardInfo, text: string) => {
    const info = {...CardDraft.read().info, [field]: text};
    CardDraft.patch({info});
    refresh();
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled">
      <ImageSection
        label="Front Image"
        base64={draft.frontImage}
        onPress={() => goToCamera('front')}
      />
      <ImageSection
        label="Back Image (MRZ)"
        base64={draft.backImage}
        onPress={() => goToCamera('back')}
      />

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Info</Text>
        {CARD_INFO_FIELDS.map(field => (
          <View style={styles.field} key={field}>
            <Text style={styles.fieldLabel}>{field}</Text>
            <TextInput
              style={styles.fieldInput}
              value={draft.info[field]}
              autoCapitalize="characters"
              onChangeText={text => updateField(field, text)}
              placeholder={`Enter ${field}`}
              placeholderTextColor="#94A3B8"
            />
          </View>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#F1F5F9'},
  content: {paddingBottom: 32},
  section: {paddingHorizontal: 20, paddingTop: 20},
  sectionTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#475569',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    marginBottom: 10,
  },
  cardImage: {
    width: '100%',
    aspectRatio: 85.6 / 54,
    borderRadius: 12,
    backgroundColor: '#E2E8F0',
  },
  emptyImage: {
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: '#94A3B8',
    backgroundColor: '#FFFFFF',
  },
  emptyImageText: {fontSize: 14, color: '#64748B'},
  field: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingTop: 10,
    marginBottom: 10,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: '#E2E8F0',
  },
  fieldLabel: {fontSize: 11, color: '#64748B', textTransform: 'uppercase'},
  fieldInput: {
    fontSize: 16,
    color: '#0F172A',
    paddingVertical: 8,
    paddingHorizontal: 0,
  },
});
