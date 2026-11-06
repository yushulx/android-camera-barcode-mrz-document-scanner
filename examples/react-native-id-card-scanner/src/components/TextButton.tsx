import React from 'react';
import {StyleSheet, Text, TouchableOpacity} from 'react-native';

interface TextButtonProps {
  title: string;
  onPress: () => void;
  disabled?: boolean;
}

/** Plain text button for the navigation header. */
export function TextButton({title, onPress, disabled}: TextButtonProps) {
  return (
    <TouchableOpacity onPress={onPress} disabled={disabled} hitSlop={12}>
      <Text style={[styles.title, disabled && styles.disabled]}>{title}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  title: {color: '#FFFFFF', fontSize: 16, fontWeight: '600'},
  disabled: {opacity: 0.4},
});
