import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  StatusBar,
  Platform,
  Animated,
  KeyboardAvoidingView,
} from 'react-native';

function resolveUserId(phone) {
  return phone.startsWith('8') ? 2 : 1;
}

export default function OtpScreen({ route, navigation }) {
  const { phone } = route.params;
  const [otp, setOtp]       = useState('');
  const [hasError, setError] = useState(false);
  const inputRef = useRef(null);

  const maskedPhone = `+91 ${phone.slice(0, 2)}XXXXXX${phone.slice(-2)}`;

  // Entrance animation
  const contentAnim = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    Animated.timing(contentAnim, { toValue: 1, duration: 420, useNativeDriver: true }).start();
  }, []);

  // Shake animation for wrong OTP
  const shakeAnim = useRef(new Animated.Value(0)).current;
  const shake = () => {
    shakeAnim.setValue(0);
    Animated.sequence([
      Animated.timing(shakeAnim, { toValue:  8, duration: 60, useNativeDriver: true }),
      Animated.timing(shakeAnim, { toValue: -8, duration: 60, useNativeDriver: true }),
      Animated.timing(shakeAnim, { toValue:  6, duration: 60, useNativeDriver: true }),
      Animated.timing(shakeAnim, { toValue: -6, duration: 60, useNativeDriver: true }),
      Animated.timing(shakeAnim, { toValue:  0, duration: 60, useNativeDriver: true }),
    ]).start();
  };

  const handleVerify = () => {
    if (otp !== '000000') {
      setError(true);
      shake();
      setTimeout(() => setError(false), 2200);
      return;
    }
    navigation.replace('Home', { phone, userId: resolveUserId(phone) });
  };

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <StatusBar barStyle="dark-content" backgroundColor="#fff" />

      {/* Back */}
      <View>
        <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()} activeOpacity={0.7}>
          <Text style={styles.backArrow}>←</Text>
        </TouchableOpacity>
      </View>

      {/* Title */}
      <Animated.View
        style={{
          transform: [{ translateY: contentAnim.interpolate({ inputRange: [0, 1], outputRange: [16, 0] }) }],
        }}
      >
        <Text style={styles.title}>Enter code</Text>
        <Text style={styles.subtitle}>Sent to <Text style={styles.phoneHighlight}>{maskedPhone}</Text></Text>
      </Animated.View>

      {/* OTP Boxes */}
      <Animated.View
        style={{
          transform: [
            { translateY: contentAnim.interpolate({ inputRange: [0, 1], outputRange: [20, 0] }) },
            { translateX: shakeAnim },
          ],
        }}
      >
        <TouchableOpacity activeOpacity={1} onPress={() => inputRef.current?.focus()} style={styles.otpRow}>
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <View
              key={i}
              style={[
                styles.otpBox,
                i < otp.length && styles.otpBoxFilled,
                i === otp.length && styles.otpBoxActive,
                hasError && styles.otpBoxError,
              ]}
            >
              <Text style={[styles.otpDigit, hasError && styles.otpDigitError]}>
                {otp[i] ? '●' : ''}
              </Text>
            </View>
          ))}
        </TouchableOpacity>

        {hasError ? (
          <Text style={styles.errorText}>Incorrect code. Try again.</Text>
        ) : (
          <Text style={styles.hintRow}>
            Demo code: <Text style={styles.hintCode}>000000</Text>
          </Text>
        )}
      </Animated.View>

      {/* Hidden input */}
      <TextInput
        ref={inputRef}
        value={otp}
        onChangeText={(t) => {
          setError(false);
          setOtp(t.replace(/[^0-9]/g, '').slice(0, 6));
        }}
        keyboardType="number-pad"
        maxLength={6}
        autoFocus
        style={styles.hiddenInput}
      />

      {/* Button */}
      <View>
        <TouchableOpacity
          style={[styles.btn, otp.length < 6 && styles.btnDisabled]}
          onPress={handleVerify}
          disabled={otp.length < 6}
          activeOpacity={0.82}
        >
          <Text style={styles.btnText}>Verify & Continue</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#fff',
    paddingHorizontal: 28,
    paddingTop: Platform.OS === 'ios' ? 64 : 48,
    paddingBottom: Platform.OS === 'ios' ? 48 : 32,
    justifyContent: 'space-between',
  },

  // Back
  backBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#F2F2F2',
    justifyContent: 'center',
    alignItems: 'center',
  },
  backArrow: {
    fontSize: 20,
    color: '#000',
    fontWeight: '600',
    lineHeight: 22,
  },

  // Title
  title: {
    fontSize: 36,
    fontWeight: '800',
    color: '#000',
    letterSpacing: -1.2,
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 15,
    color: '#999',
    lineHeight: 22,
  },
  phoneHighlight: {
    color: '#000',
    fontWeight: '700',
  },

  // OTP
  otpRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  otpBox: {
    width: 48,
    height: 58,
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: '#E8E8E8',
    backgroundColor: '#FAFAFA',
    justifyContent: 'center',
    alignItems: 'center',
  },
  otpBoxFilled: {
    borderColor: '#000',
    backgroundColor: '#fff',
  },
  otpBoxActive: {
    borderColor: '#000',
    borderWidth: 2,
    backgroundColor: '#fff',
  },
  otpBoxError: {
    borderColor: '#E53935',
    backgroundColor: '#FFF5F5',
  },
  otpDigit: {
    fontSize: 14,
    fontWeight: '900',
    color: '#000',
  },
  otpDigitError: {
    color: '#E53935',
  },
  hintRow: {
    fontSize: 13,
    color: '#ADADAD',
    textAlign: 'center',
  },
  hintCode: {
    color: '#000',
    fontWeight: '700',
    letterSpacing: 3,
  },
  errorText: {
    fontSize: 13,
    color: '#E53935',
    textAlign: 'center',
    fontWeight: '500',
  },
  hiddenInput: {
    position: 'absolute',
    opacity: 0,
    top: 0,
    left: 0,
    height: 1,
    width: 1,
  },

  // Button
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
