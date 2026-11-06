import React, {useEffect, useState} from 'react';
import {Image, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {IDCardManager} from '../services/IDCardManager';
import type {ScannedIDCard} from '../models/ScannedIDCard';

interface CardProps {
  cardKey: string;
  onPress: (card: ScannedIDCard | null) => void;
  onLongPress: (card: ScannedIDCard | null) => void;
}

/**
 * A row in the home list. The record is loaded by key instead of being passed
 * down, so the list does not have to hold every base64 image in memory.
 */
export function Card({cardKey, onPress, onLongPress}: CardProps) {
  const [card, setCard] = useState<ScannedIDCard | null>(null);

  useEffect(() => {
    let cancelled = false;
    IDCardManager.load(cardKey).then(loaded => {
      if (!cancelled) {
        setCard(loaded);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [cardKey]);

  const fullName = [card?.info.GivenName, card?.info.Surname]
    .filter(Boolean)
    .join(' ');
  const scannedAt = card ? new Date(card.timestamp).toLocaleString() : '';

  return (
    <TouchableOpacity
      activeOpacity={0.7}
      onPress={() => onPress(card)}
      onLongPress={() => onLongPress(card)}>
      <View style={styles.row}>
        {card?.frontImage ? (
          <Image
            style={styles.thumbnail}
            source={{uri: `data:image/jpeg;base64,${card.frontImage}`}}
          />
        ) : (
          <View style={[styles.thumbnail, styles.thumbnailEmpty]}>
            <Text style={styles.thumbnailText}>No image</Text>
          </View>
        )}
        <View style={styles.details}>
          <Text style={styles.name} numberOfLines={1}>
            {fullName || 'Unnamed card'}
          </Text>
          <Text style={styles.number} numberOfLines={1}>
            {card?.info.IDNumber || '—'}
          </Text>
          <Text style={styles.date} numberOfLines={1}>
            {scannedAt}
          </Text>
        </View>
        <Text style={styles.chevron}>›</Text>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginTop: 12,
    padding: 12,
    borderRadius: 14,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: '#E2E8F0',
  },
  thumbnail: {
    width: 96,
    height: 61,
    borderRadius: 8,
    backgroundColor: '#E2E8F0',
  },
  thumbnailEmpty: {alignItems: 'center', justifyContent: 'center'},
  thumbnailText: {fontSize: 11, color: '#94A3B8'},
  details: {flex: 1, paddingHorizontal: 12},
  name: {fontSize: 15, fontWeight: '700', color: '#0F172A'},
  number: {fontSize: 13, color: '#475569', marginTop: 2},
  date: {fontSize: 11, color: '#94A3B8', marginTop: 4},
  chevron: {fontSize: 24, color: '#CBD5E1', marginRight: 4},
});
