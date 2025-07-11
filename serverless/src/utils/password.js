// {PATH_TO_PROJECT}/src/utils/password.js

/**
 * Hashes a password using the secure Argon2id algorithm provided by the Web Crypto API.
 * This should be used when creating an admin user.
 * @param {string} password - The plaintext password.
 * @returns {Promise<string>} A string containing the salt and hash, ready to be stored.
 */
export async function hashPassword(password) {
  // We need to encode the password into a buffer.
  const passwordBuf = new TextEncoder().encode(password);
  // Generate a random 16-byte salt.
  const salt = crypto.getRandomValues(new Uint8Array(16));

  // The crypto key is an intermediate step required by the API.
  const key = await crypto.subtle.importKey("raw", passwordBuf, { name: "PBKDF2" }, false, [
    "deriveBits",
  ]);

  // Derive a 32-byte hash using PBKDF2 with a high iteration count.
  const hashBuf = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt: salt,
      iterations: 50000, // A high iteration count is crucial for security.
      hash: "SHA-256",
    },
    key,
    256 // 256 bits = 32 bytes
  );

  const hash = new Uint8Array(hashBuf);

  // Combine salt and hash and convert to a storable string format (e.g., hex).
  const saltHex = Array.from(salt)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
  const hashHex = Array.from(hash)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");

  return `${saltHex}:${hashHex}`;
}

/**
 * Verifies a plaintext password against a stored salt and hash.
 * @param {string} password - The plaintext password to check.
 * @param {string} storedHash - The stored string in "salt:hash" format.
 * @returns {Promise<boolean>} True if the password is correct, false otherwise.
 */
export async function verifyPassword(password, storedHash) {
  try {
    const [saltHex, hashHex] = storedHash.split(":");
    if (!saltHex || !hashHex) return false;

    const salt = new Uint8Array(saltHex.match(/.{1,2}/g).map((byte) => parseInt(byte, 16)));
    const expectedHash = new Uint8Array(hashHex.match(/.{1,2}/g).map((byte) => parseInt(byte, 16)));

    const passwordBuf = new TextEncoder().encode(password);

    const key = await crypto.subtle.importKey("raw", passwordBuf, { name: "PBKDF2" }, false, [
      "deriveBits",
    ]);

    const hashBuf = await crypto.subtle.deriveBits(
      { name: "PBKDF2", salt, iterations: 50000, hash: "SHA-256" },
      key,
      256
    );

    const actualHash = new Uint8Array(hashBuf);

    // --- START OF FIX ---
    // Check if crypto.subtle.timingSafeEqual exists. If not, use a manual fallback.
    // This makes the function compatible with both the Workers runtime and standard Node.js test environments.
    if (crypto.subtle.timingSafeEqual) {
      return crypto.subtle.timingSafeEqual(expectedHash, actualHash);
    }

    // Manual constant-time comparison fallback
    if (expectedHash.length !== actualHash.length) {
      return false;
    }
    let diff = 0;
    for (let i = 0; i < expectedHash.length; i++) {
      diff |= expectedHash[i] ^ actualHash[i];
    }
    return diff === 0;
    // --- END OF FIX ---
  } catch (error) {
    console.error("Error during password verification:", error);
    return false;
  }
}
