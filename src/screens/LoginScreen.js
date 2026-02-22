import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  StatusBar,
  KeyboardAvoidingView,
  Platform,
  Animated,
} from 'react-native';

export default function LoginScreen({ navigation }) {
  const [phone, setPhone] = useState('');
  const [focused, setFocused] = useState(false);

  const isValid = /^[789]\d{9}$/.test(phone);

  // Entrance animations
  const wordmarkAnim  = useRef(new Animated.Value(0)).current;
  const taglineAnim   = useRef(new Animated.Value(0)).current;
  const cardTranslate = useRef(new Animated.Value(60)).current;
  const cardOpacity   = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.sequence([
      Animated.timing(wordmarkAnim, { toValue: 1, duration: 600, useNativeDriver: true }),
      Animated.timing(taglineAnim,  { toValue: 1, duration: 400, useNativeDriver: true }),
      Animated.parallel([
        Animated.spring(cardTranslate, { toValue: 0, tension: 50, friction: 11, useNativeDriver: true }),
        Animated.timing(cardOpacity,   { toValue: 1, duration: 350, useNativeDriver: true }),
      ]),
    ]).start();
  }, []);

  return (
    <KeyboardAvoidingView
      style={styles.root}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <StatusBar barStyle="light-content" backgroundColor="#000" />

      {/* ── Hero ── */}
      <View style={styles.hero}>
        <Animated.View
          style={{
            opacity: wordmarkAnim,
            transform: [{ translateY: wordmarkAnim.interpolate({ inputRange: [0, 1], outputRange: [-16, 0] }) }],
          }}
        >
          <View style={styles.monogram}>
            <Text style={styles.monogramText}>W</Text>
          </View>
          <Text style={styles.appName}>WalletX</Text>
        </Animated.View>

        <Animated.Text
          style={[
            styles.tagline,
            { opacity: taglineAnim, transform: [{ translateY: taglineAnim.interpolate({ inputRange: [0, 1], outputRange: [8, 0] }) }] },
          ]}
        >
          Move money, simply.
        </Animated.Text>
      </View>

      {/* ── Input card ── */}
      <Animated.View style={[styles.card, { opacity: cardOpacity, transform: [{ translateY: cardTranslate }] }]}>
        <Text style={styles.cardLabel}>Phone number</Text>
        <Text style={styles.cardSub}>We'll send a one-time verification code</Text>

        <View style={[styles.phoneRow, focused && styles.phoneRowFocused]}>
          <View style={styles.prefix}>
            <Text style={styles.prefixText}>+91</Text>
          </View>
          <TextInput
            style={styles.phoneInput}
            placeholder="00000 00000"
            keyboardType="number-pad"
            maxLength={10}
            value={phone}
            onChangeText={setPhone}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            placeholderTextColor="#C4C4C4"
          />
        </View>

        <Text style={styles.hint}>Must start with 7, 8 or 9 · 10 digits</Text>

        <TouchableOpacity
          style={[styles.btn, !isValid && styles.btnDisabled]}
          onPress={() => isValid && navigation.navigate('Otp', { phone })}
          activeOpacity={0.82}
        >
          <Text style={styles.btnText}>Continue</Text>
        </TouchableOpacity>
      </Animated.View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#000',
    paddingHorizontal: 24,
    paddingTop: Platform.OS === 'ios' ? 80 : 56,
    paddingBottom: Platform.OS === 'ios' ? 48 : 32,
    justifyContent: 'space-between',
  },

  // Hero
  hero: {
    flex: 1,
    justifyContent: 'center',
  },
  monogram: {
    width: 54,
    height: 54,
    borderRadius: 16,
    backgroundColor: '#fff',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 28,
  },
  monogramText: {
    fontSize: 28,
    fontWeight: '900',
    color: '#000',
    letterSpacing: -1,
  },
  appName: {
    fontSize: 48,
    fontWeight: '800',
    color: '#fff',
    letterSpacing: -2,
    marginBottom: 12,
  },
  tagline: {
    fontSize: 18,
    color: '#666',
    fontWeight: '400',
    letterSpacing: 0.1,
  },

  // Card
  card: {
    backgroundColor: '#fff',
    borderRadius: 24,
    padding: 28,
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 32,
    shadowOffset: { width: 0, height: -4 },
    elevation: 16,
  },
  cardLabel: {
    fontSize: 20,
    fontWeight: '700',
    color: '#000',
    letterSpacing: -0.4,
    marginBottom: 6,
  },
  cardSub: {
    fontSize: 13,
    color: '#999',
    marginBottom: 24,
    lineHeight: 18,
  },
  phoneRow: {
    flexDirection: 'row',
    borderWidth: 1.5,
    borderColor: '#E8E8E8',
    borderRadius: 14,
    overflow: 'hidden',
    marginBottom: 10,
    backgroundColor: '#FAFAFA',
  },
  phoneRowFocused: {
    borderColor: '#000',
    backgroundColor: '#fff',
  },
  prefix: {
    paddingHorizontal: 16,
    paddingVertical: 17,
    justifyContent: 'center',
    borderRightWidth: 1.5,
    borderRightColor: '#E8E8E8',
  },
  prefixText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#000',
  },
  phoneInput: {
    flex: 1,
    paddingHorizontal: 16,
    paddingVertical: 17,
    fontSize: 18,
    fontWeight: '600',
    color: '#000',
    letterSpacing: 1.5,
  },
  hint: {
    fontSize: 11,
    color: '#C4C4C4',
    marginBottom: 24,
    letterSpacing: 0.2,
  },
  btn: {
    backgroundColor: '#000',
    borderRadius: 14,
    paddingVertical: 18,
    alignItems: 'center',
  },
  btnDisabled: {
    backgroundColor: '#D8D8D8',
  },
  btnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
});
