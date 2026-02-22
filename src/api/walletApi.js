import axios from 'axios';

// iOS Simulator  → use localhost
// Android Emulator → use 10.0.2.2
// Physical device  → use your machine's LAN IP (shown in `npx expo start` as exp://<IP>:8081)
const BASE_URL = 'http://172.20.10.2:8080/api/v1';

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
});

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Retries `fn` up to `maxAttempts` times on transient failures only.
 *
 * Retryable:  no HTTP response (network drop / timeout)  OR  HTTP 503
 * Not retried: 400, 404, 409, 422, 500 — these are permanent errors
 *
 * The idempotency key is generated ONCE by the caller and reused on
 * every attempt.  If attempt 1 committed but the response was lost in
 * transit, attempt 2 sends the same key → server returns the cached
 * response without processing a duplicate transaction.
 */
async function withRetry(fn, maxAttempts = 3, baseDelayMs = 600) {
  let lastErr;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (err) {
      lastErr = err;
      const isTransient = !err.response || err.response.status === 503;
      if (!isTransient || attempt === maxAttempts) throw err;
      // Exponential back-off: 600ms → 1200ms
      await new Promise((res) => setTimeout(res, baseDelayMs * attempt));
    }
  }
  throw lastErr;
}

export const getBalance = (userId, assetType) =>
  api.get(`/wallets/${userId}/balance`, { params: { assetType } });

// Returns LedgerEntryResponse[] sorted most-recent-first
export const getLedger = (userId, assetType) =>
  api.get(`/wallets/${userId}/ledger`, { params: { assetType } });

export const topUp = (userId, assetType, amount, referenceId) => {
  const idempotencyKey = generateUUID(); // one key shared across all retry attempts
  return withRetry(() =>
    api.post(
      '/transactions/topup',
      { userId, assetType, amount, referenceId },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    ),
  );
};

export const spend = (userId, assetType, amount, service) => {
  const idempotencyKey = generateUUID();
  return withRetry(() =>
    api.post(
      '/transactions/spend',
      { userId, assetType, amount, service },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    ),
  );
};

export const bonus = (userId, assetType, amount, reason) => {
  const idempotencyKey = generateUUID();
  return withRetry(() =>
    api.post(
      '/transactions/bonus',
      { userId, assetType, amount, reason },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    ),
  );
};
