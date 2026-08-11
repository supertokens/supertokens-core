import * as crypto from 'crypto';

// Minimal RFC 4648 base32 + RFC 6238 TOTP, matched to the core's TOTP
// implementation (io.supertokens.totp.Totp): HmacSHA1, 6 digits, base32 secret,
// code accepted for any counter within +/- skew periods of now. We use this to
// mint valid codes ourselves so we can seed a large number of used codes for a
// single dedicated user (the TOTPQueries "get all used codes" path).

const BASE32_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

/** Encode bytes as RFC 4648 base32 (uppercase, with '=' padding). */
export const base32Encode = (bytes: Buffer): string => {
  let bits = 0;
  let value = 0;
  let output = '';
  for (const byte of bytes) {
    value = (value << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      output += BASE32_ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) {
    output += BASE32_ALPHABET[(value << (5 - bits)) & 31];
  }
  while (output.length % 8 !== 0) {
    output += '=';
  }
  return output;
};

/** Decode an RFC 4648 base32 string (case-insensitive, ignores padding). */
export const base32Decode = (input: string): Buffer => {
  const clean = input.toUpperCase().replace(/=+$/, '');
  let bits = 0;
  let value = 0;
  const out: number[] = [];
  for (const ch of clean) {
    const idx = BASE32_ALPHABET.indexOf(ch);
    if (idx === -1) continue;
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(out);
};

/** Generate a random base32 secret (20 bytes = 160 bits, as the core does). */
export const generateBase32Secret = (): string => base32Encode(crypto.randomBytes(20));

/**
 * Compute the RFC 6238 TOTP for a given time-step counter (floor(epochSeconds /
 * period) + offset). HmacSHA1, 6 digits — identical to the core.
 */
export const totpForCounter = (secretBase32: string, counter: number, digits = 6): string => {
  const key = base32Decode(secretBase32);
  const buf = Buffer.alloc(8);
  // 8-byte big-endian counter (safe for counters well within 2^53).
  buf.writeBigUInt64BE(BigInt(counter));
  const hmac = crypto.createHmac('sha1', key).update(buf).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const binCode =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff);
  const code = binCode % 10 ** digits;
  return code.toString().padStart(digits, '0');
};

/** Counter for the current time given a device period (seconds). */
export const currentCounter = (period: number): number => Math.floor(Date.now() / 1000 / period);
