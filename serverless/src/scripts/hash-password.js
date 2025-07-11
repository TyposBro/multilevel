// This script uses the exact same algorithm as your worker's verifyPassword function.

export async function hashPassword(password) {
  const passwordBuf = new TextEncoder().encode(password);
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey("raw", passwordBuf, { name: "PBKDF2" }, false, [
    "deriveBits",
  ]);
  const hashBuf = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations: 50000, hash: "SHA-256" },
    key,
    256
  );
  const hash = new Uint8Array(hashBuf);
  const saltHex = Array.from(salt)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
  const hashHex = Array.from(hash)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
  return `${saltHex}:${hashHex}`;
}

// This check ensures the CLI logic only runs when the script is executed directly
if (import.meta.url.startsWith("file:") && process.argv[1] === new URL(import.meta.url).pathname) {
  (async () => {
    const passwordToHash = process.argv[2];
    if (!passwordToHash) {
      console.error('Usage: node src/scripts/hash-password.mjs "Your-Secure-Password-Here"');
      process.exit(1);
    }
    const hashedPassword = await hashPassword(passwordToHash);
    console.log("--- Copy the entire line below ---");
    console.log(hashedPassword);
  })();
}
