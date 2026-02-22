import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  Modal,
  TextInput,
  Alert,
  ActivityIndicator,
  StatusBar,
  KeyboardAvoidingView,
  Animated,
  Keyboard,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { getBalance, topUp, spend, bonus, getLedger } from '../api/walletApi';

const ASSETS = [
  { name: 'GOLD_COINS',     label: 'Gold Coins',    code: 'GC', accent: '#B8860B', tint: '#FFF8E1' },
  { name: 'DIAMONDS',       label: 'Diamonds',       code: 'DM', accent: '#0284C7', tint: '#E0F4FF' },
  { name: 'LOYALTY_POINTS', label: 'Loyalty Points', code: 'LP', accent: '#7C3AED', tint: '#F0EBFF' },
];

const TXN = {
  topup: { label: 'Top-up',    cta: 'Confirm Top-up',    color: '#000'     },
  spend: { label: 'Spend',     cta: 'Confirm Spend',     color: '#C62828'  },
  bonus: { label: 'Add Bonus', cta: 'Confirm Bonus',     color: '#1B5E20'  },
};

const GAME_ITEMS = [
  { id: 'UC',          label: 'UC Purchase',  sub: '60 UC',          price: 75,   service: 'UC_PURCHASE'     },
  { id: 'OUTFIT',      label: 'Outfit',        sub: 'Character skin', price: 600,  service: 'OUTFIT_PURCHASE' },
  { id: 'WEAPON_SKIN', label: 'Weapon Skin',   sub: 'Gun finisher',   price: 400,  service: 'WEAPON_SKIN'     },
  { id: 'BATTLE_PASS', label: 'Battle Pass',   sub: 'Season pass',    price: 800,  service: 'BATTLE_PASS'     },
  { id: 'EMOTE',       label: 'Emote',         sub: 'Character emote',price: 200,  service: 'EMOTE_PURCHASE'  },
];

function friendlyError(err) {
  const status = err.response?.status;
  if (!status) return 'No connection. Check your network and try again.';
  if (status === 409) return 'Insufficient balance to complete this transaction.';
  if (status === 400) return 'Invalid request. Please check the amount and try again.';
  if (status === 404) return 'Account not found. Please log in again.';
  if (status === 422) return 'Invalid amount entered.';
  if (status === 503) return 'Service temporarily unavailable. Try again shortly.';
  return 'Something went wrong. Please try again.';
}

function successMsg(type, asset, amt, selectedItem) {
  if (type === 'topup') return `${formatAmt(amt)} ${asset.label} added to your wallet.`;
  if (type === 'spend') return `${selectedItem?.label ?? 'Purchase'} complete. ${formatAmt(amt)} deducted.`;
  if (type === 'bonus') return `Bonus of ${formatAmt(amt)} ${asset.label} credited.`;
  return 'Transaction successful.';
}

function formatTimestamp(ts) {
  return new Date(ts).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

function formatAmt(value) {
  if (value === undefined || value === null) return '—';
  return parseFloat(value).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export default function HomeScreen({ route, navigation }) {
  const { phone, userId } = route.params;

  const [balances,      setBalances]      = useState({});
  const [loading,       setLoading]       = useState(true);
  const [refreshing,    setRefreshing]    = useState(false);
  const [modal,         setModal]         = useState(null);   // { asset, type }
  const [amount,        setAmount]        = useState('');
  const [txnLoading,    setTxnLoading]    = useState(false);
  const [ledgerModal,   setLedgerModal]   = useState(null);   // { asset }
  const [ledger,        setLedger]        = useState([]);
  const [ledgerLoading, setLedgerLoading] = useState(false);
  const [toast,         setToast]         = useState(null);
  const [showDropdown,  setShowDropdown]  = useState(false);
  const [selectedItem,  setSelectedItem]  = useState(null);  // for spend: GAME_ITEMS entry
  const [headerHeight,  setHeaderHeight]  = useState(72);
  const [keyboardHeight, setKeyboardHeight] = useState(0);

  useEffect(() => {
    const show = Keyboard.addListener('keyboardDidShow', (e) => setKeyboardHeight(e.endCoordinates.height));
    const hide = Keyboard.addListener('keyboardDidHide', () => setKeyboardHeight(0));
    return () => { show.remove(); hide.remove(); };
  }, []);

  // Animations
  const toastAnim    = useRef(new Animated.Value(0)).current;
  const dropdownAnim = useRef(new Animated.Value(0)).current;
  const headerAnim = useRef(new Animated.Value(0)).current;
  const cardAnims  = useRef(ASSETS.map(() => new Animated.Value(0))).current;

  // ── Toast ──────────────────────────────────────────────────────────────
  const showToast = useCallback((message, type = 'success') => {
    setToast({ message, type });
    toastAnim.setValue(0);
    Animated.sequence([
      Animated.timing(toastAnim, { toValue: 1, duration: 260, useNativeDriver: true }),
      Animated.delay(2600),
      Animated.timing(toastAnim, { toValue: 0, duration: 260, useNativeDriver: true }),
    ]).start(() => setToast(null));
  }, [toastAnim]);

  // ── Balances ───────────────────────────────────────────────────────────
  const fetchBalances = useCallback(async () => {
    // Promise.allSettled — a single asset 404 / timeout does NOT crash the whole screen.
    // Fulfilled results update their balance; rejected ones keep showing '—'.
    const results = await Promise.allSettled(ASSETS.map((a) => getBalance(userId, a.name)));

    const map = {};
    let failedCount = 0;
    results.forEach((res, i) => {
      if (res.status === 'fulfilled') {
        map[ASSETS[i].name] = res.value.data.balance;
      } else {
        failedCount++;
      }
    });

    setBalances((prev) => ({ ...prev, ...map }));

    if (failedCount === ASSETS.length) {
      // All failed — almost certainly a connectivity issue
      Alert.alert('Connection Error', 'Could not reach the backend.\n\nMake sure the Spring Boot server is running on port 8080.');
    } else if (failedCount > 0) {
      showToast(`${failedCount} balance(s) could not be loaded`, 'error');
    }

    setLoading(false);
    setRefreshing(false);
  }, [userId]);

  useEffect(() => { fetchBalances(); }, [fetchBalances]);

  // ── Card entrance after load ───────────────────────────────────────────
  useEffect(() => {
    if (!loading) {
      Animated.parallel([
        Animated.timing(headerAnim, { toValue: 1, duration: 400, useNativeDriver: true }),
        Animated.stagger(90, cardAnims.map((a) =>
          Animated.spring(a, { toValue: 1, tension: 60, friction: 10, useNativeDriver: true })
        )),
      ]).start();
    }
  }, [loading]);

  // ── Transaction ────────────────────────────────────────────────────────
  const openModal = (asset, type) => { setAmount(''); setSelectedItem(null); setModal({ asset, type }); };

  const handleTransaction = async () => {
    const amt = parseFloat(amount);
    if (!amount || isNaN(amt) || amt <= 0) {
      Alert.alert('Invalid Amount', 'Please enter a valid amount greater than 0.');
      return;
    }
    setTxnLoading(true);
    const { asset, type } = modal;
    try {
      if (type === 'topup') await topUp(userId, asset.name, amt, `REF-${Date.now()}`);
      if (type === 'spend') await spend(userId, asset.name, amt, selectedItem?.service ?? 'IN_GAME_ITEM');
      if (type === 'bonus') await bonus(userId, asset.name, amt, 'REFERRAL_BONUS');
      Keyboard.dismiss();
      setModal(null);
      await fetchBalances();
      showToast(successMsg(type, asset, amt, selectedItem), 'success');
    } catch (err) {
      Keyboard.dismiss();
      setModal(null);
      showToast(friendlyError(err), 'error');
    } finally {
      setTxnLoading(false);
    }
  };

  // ── Ledger ─────────────────────────────────────────────────────────────
  const openLedger = async (asset) => {
    setLedgerModal({ asset });
    setLedger([]);
    setLedgerLoading(true);
    try {
      const res = await getLedger(userId, asset.name);
      setLedger(res.data);
    } catch {
      showToast('Could not load transaction history.', 'error');
      setLedgerModal(null);
    } finally {
      setLedgerLoading(false);
    }
  };

  // ── Dropdown ───────────────────────────────────────────────────────────
  const openDropdown = () => {
    setShowDropdown(true);
    dropdownAnim.setValue(0);
    Animated.spring(dropdownAnim, { toValue: 1, tension: 70, friction: 12, useNativeDriver: true }).start();
  };

  const closeDropdown = () => {
    Animated.timing(dropdownAnim, { toValue: 0, duration: 160, useNativeDriver: true })
      .start(() => setShowDropdown(false));
  };

  // ── Logout ─────────────────────────────────────────────────────────────
  const handleLogout = () => {
    closeDropdown();
    Alert.alert('Sign out', 'Are you sure?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Sign out', style: 'destructive', onPress: () => navigation.reset({ index: 0, routes: [{ name: 'Login' }] }) },
    ]);
  };

  const maskedPhone = `+91 ${phone.slice(0, 2)}XXXXXX${phone.slice(-2)}`;

  // ── Render ─────────────────────────────────────────────────────────────
  return (
    <SafeAreaView style={S.safe}>
      <StatusBar barStyle="light-content" backgroundColor="#000" />

      {/* ── Header ── */}
      <View style={S.header} onLayout={(e) => setHeaderHeight(e.nativeEvent.layout.height)}>
        <View>
          <Text style={S.greeting}>Hello</Text>
          <Text style={S.phone}>{maskedPhone}</Text>
        </View>
        <TouchableOpacity style={S.avatarBtn} onPress={showDropdown ? closeDropdown : openDropdown} activeOpacity={0.75}>
          <View style={S.avatar}>
            <Text style={S.avatarText}>U{userId}</Text>
          </View>
          <Text style={S.chevron}>{showDropdown ? '▴' : '▾'}</Text>
        </TouchableOpacity>
      </View>

      {/* ── Wallet list ── */}
      <ScrollView
        style={S.body}
        contentContainerStyle={S.scroll}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); fetchBalances(); }} tintColor="#000" colors={['#000']} />}
        showsVerticalScrollIndicator={false}
      >
        <Text style={S.sectionTitle}>Your Wallets</Text>
        <Text style={S.sectionSub}>Pull to refresh</Text>

        {loading ? (
          <ActivityIndicator size="large" color="#000" style={{ marginTop: 60 }} />
        ) : (
          ASSETS.map((asset, i) => (
            <Animated.View
              key={asset.name}
              style={{
                opacity: cardAnims[i],
                transform: [{ translateY: cardAnims[i].interpolate({ inputRange: [0, 1], outputRange: [32, 0] }) }],
              }}
            >
              <View style={S.card}>
                {/* Card header row */}
                <View style={S.cardTop}>
                  <View style={[S.codeBadge, { backgroundColor: asset.tint }]}>
                    <Text style={[S.codeText, { color: asset.accent }]}>{asset.code}</Text>
                  </View>
                  <View style={S.cardMeta}>
                    <Text style={S.assetName}>{asset.label}</Text>
                    <Text style={[S.balance, { color: asset.accent }]}>{formatAmt(balances[asset.name])}</Text>
                  </View>
                </View>

                <View style={S.sep} />

                {/* Actions */}
                <View style={S.actions}>
                  <TouchableOpacity style={S.actionBtn} onPress={() => openModal(asset, 'topup')} activeOpacity={0.65}>
                    <Text style={S.actionIcon}>+</Text>
                    <Text style={S.actionLabel}>Top-up</Text>
                  </TouchableOpacity>

                  <View style={S.actionSep} />

                  <TouchableOpacity style={S.actionBtn} onPress={() => openModal(asset, 'spend')} activeOpacity={0.65}>
                    <Text style={[S.actionIcon, { color: '#C62828' }]}>−</Text>
                    <Text style={[S.actionLabel, { color: '#C62828' }]}>Spend</Text>
                  </TouchableOpacity>

                  <View style={S.actionSep} />

                  <TouchableOpacity style={S.actionBtn} onPress={() => openModal(asset, 'bonus')} activeOpacity={0.65}>
                    <Text style={[S.actionIcon, { color: '#2E7D32' }]}>★</Text>
                    <Text style={[S.actionLabel, { color: '#2E7D32' }]}>Bonus</Text>
                  </TouchableOpacity>
                </View>

                <View style={S.sep} />

                {/* History */}
                <TouchableOpacity style={S.historyRow} onPress={() => openLedger(asset)} activeOpacity={0.65}>
                  <Text style={S.historyLabel}>Transaction History</Text>
                  <Text style={S.historyChevron}>›</Text>
                </TouchableOpacity>
              </View>
            </Animated.View>
          ))
        )}

        <Text style={S.footer}>WalletX · Internal Wallet Service</Text>
      </ScrollView>

      {/* ── Transaction Sheet ── */}
      <Modal visible={!!modal} transparent animationType="slide" onRequestClose={() => !txnLoading && setModal(null)}>
        <KeyboardAvoidingView style={S.overlay} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <TouchableOpacity style={S.scrim} activeOpacity={1} onPress={() => !txnLoading && setModal(null)} />
          <View style={S.sheet}>
            {modal && (
              <>
                <View style={S.handle} />
                <Text style={S.sheetTitle}>{TXN[modal.type].label}</Text>
                <Text style={S.sheetSub}>{modal.asset.label}</Text>

                <View style={S.balanceRow}>
                  <Text style={S.balanceLabel}>Current balance</Text>
                  <Text style={S.balanceVal}>{formatAmt(balances[modal.asset.name])}</Text>
                </View>

                {modal.type === 'spend' && (
                  <View style={S.itemGrid}>
                    {GAME_ITEMS.map((item) => {
                      const active = selectedItem?.id === item.id;
                      return (
                        <TouchableOpacity
                          key={item.id}
                          style={[S.itemCard, active && S.itemCardActive]}
                          onPress={() => { setSelectedItem(item); setAmount(String(item.price)); }}
                          activeOpacity={0.72}
                        >
                          <Text style={[S.itemLabel, active && S.itemLabelActive]}>{item.label}</Text>
                          <Text style={[S.itemSub, active && S.itemSubActive]}>{item.sub}</Text>
                          <Text style={[S.itemPrice, active && S.itemPriceActive]}>{item.price.toLocaleString('en-IN')}</Text>
                        </TouchableOpacity>
                      );
                    })}
                  </View>
                )}

                <TextInput
                  style={S.amtInput}
                  placeholder="0.00"
                  keyboardType="decimal-pad"
                  value={amount}
                  onChangeText={(text) => {
                    const clean = text.replace(/[^0-9.]/g, '');
                    const parts = clean.split('.');
                    if (parts.length > 2) return;           // block second decimal point
                    if (parts[0].length > 6) return;        // max 6 digits before decimal
                    if (parts[1] !== undefined && parts[1].length > 2) return; // max 2 decimal places
                    setAmount(clean);
                  }}
                  placeholderTextColor="#D0D0D0"
                  autoFocus
                />

                <TouchableOpacity
                  style={[S.ctaBtn, { backgroundColor: TXN[modal.type].color }, (txnLoading || (modal.type === 'spend' && !selectedItem)) && S.ctaBtnDisabled]}
                  onPress={handleTransaction}
                  disabled={txnLoading || (modal.type === 'spend' && !selectedItem)}
                  activeOpacity={0.82}
                >
                  {txnLoading
                    ? <ActivityIndicator color="#fff" />
                    : <Text style={S.ctaText}>{TXN[modal.type].cta}</Text>
                  }
                </TouchableOpacity>
              </>
            )}
          </View>
        </KeyboardAvoidingView>
      </Modal>

      {/* ── Ledger Sheet ── */}
      <Modal visible={!!ledgerModal} transparent animationType="slide" onRequestClose={() => setLedgerModal(null)}>
        <View style={S.overlay}>
          <TouchableOpacity style={S.scrim} activeOpacity={1} onPress={() => setLedgerModal(null)} />
          <View style={[S.sheet, S.ledgerSheet]}>
            <View style={S.handle} />
            {ledgerModal && (
              <>
                <Text style={S.sheetTitle}>{ledgerModal.asset.label}</Text>
                <Text style={S.sheetSub}>Transaction history</Text>

                {ledgerLoading ? (
                  <ActivityIndicator size="large" color="#000" style={{ marginVertical: 48 }} />
                ) : ledger.length === 0 ? (
                  <View style={S.emptyBox}>
                    <Text style={S.emptyText}>No transactions yet</Text>
                  </View>
                ) : (
                  <ScrollView showsVerticalScrollIndicator={false}>
                    {ledger.map((entry) => {
                      const credit = entry.entryType === 'CREDIT';
                      return (
                        <View key={entry.ledgerEntryId} style={S.ledgerRow}>
                          <View style={[S.ledgerDot, { backgroundColor: credit ? '#E8F5E9' : '#FFEBEE' }]}>
                            <Text style={[S.ledgerDotText, { color: credit ? '#2E7D32' : '#C62828' }]}>
                              {credit ? '+' : '−'}
                            </Text>
                          </View>
                          <View style={S.ledgerMeta}>
                            <Text style={S.ledgerType}>{entry.transactionType}</Text>
                            <Text style={S.ledgerRef} numberOfLines={1}>{entry.referenceId}</Text>
                            <Text style={S.ledgerTime}>{formatTimestamp(entry.timestamp)}</Text>
                          </View>
                          <Text style={[S.ledgerAmt, { color: credit ? '#2E7D32' : '#C62828' }]}>
                            {credit ? '+' : '−'}{parseFloat(entry.amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                          </Text>
                        </View>
                      );
                    })}
                  </ScrollView>
                )}
              </>
            )}
          </View>
        </View>
      </Modal>

      {/* ── User Dropdown ── */}
      {showDropdown && (
        <>
          <TouchableOpacity style={S.dropdownScrim} activeOpacity={1} onPress={closeDropdown} />
          <Animated.View
            style={[
              S.dropdown,
              { top: headerHeight },
              {
                opacity: dropdownAnim,
                transform: [{ translateY: dropdownAnim.interpolate({ inputRange: [0, 1], outputRange: [-8, 0] }) }],
              },
            ]}
          >
            {/* User info */}
            <View style={S.dropdownUser}>
              <View style={S.dropdownAvatar}>
                <Text style={S.dropdownAvatarText}>U{userId}</Text>
              </View>
              <View>
                <Text style={S.dropdownName}>User {userId}</Text>
                <Text style={S.dropdownPhone}>{maskedPhone}</Text>
              </View>
            </View>

            <View style={S.dropdownDivider} />

            {/* Sign out */}
            <TouchableOpacity style={S.dropdownItem} onPress={handleLogout} activeOpacity={0.7}>
              <Text style={S.dropdownItemText}>Sign out</Text>
            </TouchableOpacity>
          </Animated.View>
        </>
      )}

      {/* ── Toast ── */}
      {toast && (
        <Animated.View
          pointerEvents="none"
          style={[
            S.toast,
            { bottom: keyboardHeight > 0 ? keyboardHeight + 12 : 36 },
            { backgroundColor: toast.type === 'error' ? '#C62828' : '#111' },
            { opacity: toastAnim, transform: [{ translateY: toastAnim.interpolate({ inputRange: [0, 1], outputRange: [14, 0] }) }] },
          ]}
        >
          <View style={[S.toastDot, { backgroundColor: toast.type === 'error' ? 'rgba(255,255,255,0.5)' : '#00C48C' }]} />
          <Text style={S.toastText}>{toast.message}</Text>
        </Animated.View>
      )}
    </SafeAreaView>
  );
}

// ─────────────────────────── Styles ──────────────────────────────────────────
const S = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#000' },

  // Header
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 24,
    paddingTop: Platform.OS === 'android' ? 16 : 6,
    paddingBottom: 22,
  },
  greeting: { fontSize: 12, color: '#666', fontWeight: '500', letterSpacing: 0.4, marginBottom: 3 },
  phone:    { fontSize: 17, fontWeight: '700', color: '#fff', letterSpacing: -0.3 },

  // Avatar button
  avatarBtn: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  avatar: {
    width: 36, height: 36, borderRadius: 18,
    backgroundColor: '#1C1C1C', borderWidth: 1, borderColor: '#333',
    justifyContent: 'center', alignItems: 'center',
  },
  avatarText: { color: '#fff', fontWeight: '700', fontSize: 12, letterSpacing: 0.3 },
  chevron: { color: '#555', fontSize: 11, fontWeight: '600' },

  // Dropdown
  dropdownScrim: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, zIndex: 9 },
  dropdown: {
    position: 'absolute', right: 20, zIndex: 10,
    width: 220,
    backgroundColor: '#1A1A1A',
    borderRadius: 16,
    borderWidth: 1, borderColor: '#2A2A2A',
    shadowColor: '#000', shadowOpacity: 0.5, shadowRadius: 24, shadowOffset: { width: 0, height: 8 },
    elevation: 20, overflow: 'hidden',
  },
  dropdownUser: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 16 },
  dropdownAvatar: {
    width: 40, height: 40, borderRadius: 20,
    backgroundColor: '#2A2A2A',
    justifyContent: 'center', alignItems: 'center',
  },
  dropdownAvatarText: { color: '#fff', fontWeight: '800', fontSize: 14 },
  dropdownName:  { color: '#fff', fontWeight: '700', fontSize: 14, letterSpacing: -0.2 },
  dropdownPhone: { color: '#555', fontSize: 12, marginTop: 2 },
  dropdownDivider: { height: 1, backgroundColor: '#2A2A2A' },
  dropdownItem: { paddingHorizontal: 16, paddingVertical: 15 },
  dropdownItemText: { color: '#E53935', fontWeight: '600', fontSize: 14 },

  // Body
  body:   { flex: 1, backgroundColor: '#F5F5F5', borderTopLeftRadius: 20, borderTopRightRadius: 20 },
  scroll: { paddingHorizontal: 20, paddingTop: 24, paddingBottom: 48 },
  sectionTitle: { fontSize: 26, fontWeight: '800', color: '#000', letterSpacing: -0.8, marginBottom: 4 },
  sectionSub:   { fontSize: 12, color: '#ADADAD', marginBottom: 22 },

  // Card
  card: {
    backgroundColor: '#fff',
    borderRadius: 20,
    marginBottom: 14,
    shadowColor: '#000',
    shadowOpacity: 0.06,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 4 },
    elevation: 3,
    overflow: 'hidden',
  },
  cardTop: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18, paddingVertical: 18 },
  codeBadge: { width: 52, height: 52, borderRadius: 14, justifyContent: 'center', alignItems: 'center' },
  codeText:  { fontSize: 14, fontWeight: '800', letterSpacing: 0.5 },
  cardMeta:  { flex: 1, marginLeft: 14 },
  assetName: { fontSize: 11, fontWeight: '600', color: '#ADADAD', letterSpacing: 0.8, textTransform: 'uppercase', marginBottom: 5 },
  balance:   { fontSize: 30, fontWeight: '800', letterSpacing: -0.8 },

  sep:       { height: 1, backgroundColor: '#F5F5F5' },

  // Actions
  actions:   { flexDirection: 'row' },
  actionBtn: { flex: 1, alignItems: 'center', paddingVertical: 14 },
  actionSep: { width: 1, backgroundColor: '#F5F5F5', marginVertical: 10 },
  actionIcon:  { fontSize: 20, fontWeight: '800', color: '#000', marginBottom: 3 },
  actionLabel: { fontSize: 11, fontWeight: '700', color: '#000', letterSpacing: 0.3 },

  // History row
  historyRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 18, paddingVertical: 14 },
  historyLabel:   { fontSize: 13, fontWeight: '600', color: '#888' },
  historyChevron: { fontSize: 20, color: '#C8C8C8', fontWeight: '300' },

  footer: { textAlign: 'center', fontSize: 11, color: '#CACACA', marginTop: 16, letterSpacing: 0.3 },

  // Modal / Sheet shared
  overlay: { flex: 1, justifyContent: 'flex-end' },
  scrim:   { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)' },
  sheet: {
    backgroundColor: '#fff',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 28,
    paddingBottom: Platform.OS === 'ios' ? 44 : 32,
    paddingTop: 16,
  },
  handle: { width: 40, height: 4, backgroundColor: '#E0E0E0', borderRadius: 2, alignSelf: 'center', marginBottom: 26 },

  sheetTitle: { fontSize: 26, fontWeight: '800', color: '#000', letterSpacing: -0.6, marginBottom: 4 },
  sheetSub:   { fontSize: 13, color: '#ADADAD', marginBottom: 24 },

  balanceRow: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    backgroundColor: '#F5F5F5', borderRadius: 12,
    paddingHorizontal: 16, paddingVertical: 14, marginBottom: 20,
  },
  balanceLabel: { fontSize: 13, color: '#888' },
  balanceVal:   { fontSize: 17, fontWeight: '700', color: '#000', letterSpacing: -0.3 },

  amtInput: {
    borderWidth: 1.5, borderColor: '#E8E8E8', borderRadius: 14,
    paddingHorizontal: 18, paddingVertical: 18,
    fontSize: 34, fontWeight: '800', color: '#000',
    marginBottom: 20, textAlign: 'center', letterSpacing: -1,
  },

  ctaBtn: { borderRadius: 14, paddingVertical: 18, alignItems: 'center' },
  ctaBtnDisabled: { opacity: 0.55 },
  ctaText: { color: '#fff', fontSize: 16, fontWeight: '700', letterSpacing: 0.3 },

  // Ledger
  ledgerSheet: { maxHeight: '85%' },
  emptyBox:  { alignItems: 'center', paddingVertical: 52 },
  emptyText: { fontSize: 14, color: '#ADADAD', fontWeight: '500' },

  ledgerRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#F5F5F5' },
  ledgerDot: { width: 42, height: 42, borderRadius: 13, justifyContent: 'center', alignItems: 'center', marginRight: 14 },
  ledgerDotText: { fontSize: 20, fontWeight: '800' },
  ledgerMeta: { flex: 1 },
  ledgerType: { fontSize: 14, fontWeight: '700', color: '#000', marginBottom: 2, letterSpacing: -0.2 },
  ledgerRef:  { fontSize: 11, color: '#ADADAD', marginBottom: 2 },
  ledgerTime: { fontSize: 11, color: '#ADADAD' },
  ledgerAmt:  { fontSize: 15, fontWeight: '800', marginLeft: 10, letterSpacing: -0.3 },

  // Game item grid (spend modal)
  itemGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 20,
  },
  itemCard: {
    width: '47%',
    borderWidth: 1.5,
    borderColor: '#E8E8E8',
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 13,
    backgroundColor: '#FAFAFA',
  },
  itemCardActive: {
    borderColor: '#C62828',
    backgroundColor: '#FFF5F5',
  },
  itemLabel: {
    fontSize: 14,
    fontWeight: '700',
    color: '#000',
    marginBottom: 3,
    letterSpacing: -0.2,
  },
  itemLabelActive: { color: '#C62828' },
  itemSub: { fontSize: 11, color: '#ADADAD', fontWeight: '500', marginBottom: 6 },
  itemSubActive: { color: '#E57373' },
  itemPrice: { fontSize: 15, fontWeight: '800', color: '#000', letterSpacing: -0.4 },
  itemPriceActive: { color: '#C62828' },

  // Toast
  toast: {
    position: 'absolute', bottom: 36, left: 20, right: 20,
    flexDirection: 'row', alignItems: 'center',
    borderRadius: 16, paddingVertical: 16, paddingHorizontal: 20,
    shadowColor: '#000', shadowOpacity: 0.28, shadowRadius: 18,
    shadowOffset: { width: 0, height: 6 }, elevation: 12,
  },
  toastDot:  { width: 8, height: 8, borderRadius: 4, marginRight: 12 },
  toastText: { flex: 1, fontSize: 14, fontWeight: '600', color: '#fff', lineHeight: 20 },
});
