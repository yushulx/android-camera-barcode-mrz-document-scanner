import React, {useCallback, useState} from 'react';
import {Alert, FlatList, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {useFocusEffect} from '@react-navigation/native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {Card} from '../components/Card';
import {IDCardManager} from '../services/IDCardManager';
import {CardDraft} from '../services/CardDraft';
import type {ScannedIDCard} from '../models/ScannedIDCard';
import type {StackScreenProps} from '../App';

export function HomeScreen({navigation}: StackScreenProps<'Home'>) {
  const [cardKeys, setCardKeys] = useState<string[]>([]);

  const refresh = useCallback(async () => {
    setCardKeys(await IDCardManager.listKeys());
  }, []);

  // Reload on every focus so a card saved on the card screen shows up here.
  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh]),
  );

  const openCard = (cardKey?: string) => {
    // A new scan starts empty; an existing card is loaded from storage.
    CardDraft.reset();
    navigation.navigate('Card', {cardKey});
  };

  const confirmDelete = (card: ScannedIDCard | null) => {
    if (!card) {
      return;
    }
    Alert.alert('Delete card', 'This removes the record and both images.', [
      {text: 'Cancel', style: 'cancel'},
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          await IDCardManager.remove(card.timestamp);
          refresh();
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <Text style={styles.title}>ID Cards</Text>
        <Text style={styles.subtitle}>
          {cardKeys.length === 0
            ? 'Scan an ID card to get started'
            : `${cardKeys.length} card${cardKeys.length > 1 ? 's' : ''} on this device`}
        </Text>
      </View>

      <FlatList
        data={cardKeys}
        keyExtractor={key => key}
        contentContainerStyle={styles.listContent}
        renderItem={({item}) => (
          <Card
            cardKey={item}
            onPress={() => openCard(item)}
            onLongPress={confirmDelete}
          />
        )}
        ListEmptyComponent={
          <View style={styles.emptyState}>
            <Text style={styles.emptyIcon}>🪪</Text>
            <Text style={styles.emptyTitle}>No cards yet</Text>
            <Text style={styles.emptyBody}>
              Capture the front and back of an ID card, then read the MRZ to fill
              in the owner's details automatically.
            </Text>
          </View>
        }
      />

      <View style={styles.bottomBar}>
        <TouchableOpacity
          activeOpacity={0.85}
          style={styles.scanButton}
          onPress={() => openCard()}>
          <Text style={styles.scanLabel}>SCAN</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#F1F5F9'},
  header: {paddingHorizontal: 20, paddingTop: 12, paddingBottom: 4},
  title: {fontSize: 26, fontWeight: '800', color: '#0F172A'},
  subtitle: {fontSize: 13, color: '#64748B', marginTop: 4},
  listContent: {paddingBottom: 96, flexGrow: 1},
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 40,
    paddingTop: 80,
  },
  emptyIcon: {fontSize: 44, marginBottom: 14},
  emptyTitle: {fontSize: 17, fontWeight: '700', color: '#0F172A'},
  emptyBody: {
    fontSize: 13,
    color: '#64748B',
    textAlign: 'center',
    lineHeight: 20,
    marginTop: 8,
  },
  bottomBar: {position: 'absolute', bottom: 28, alignSelf: 'center'},
  scanButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: '#2563EB',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#2563EB',
    shadowOffset: {width: 0, height: 6},
    shadowOpacity: 0.35,
    shadowRadius: 12,
    elevation: 8,
  },
  scanLabel: {color: '#FFFFFF', fontSize: 14, fontWeight: '800', letterSpacing: 1},
});
