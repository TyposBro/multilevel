async function hashPassword(password) {
  const passwordBuf = new TextEncoder().encode(password);
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey("raw", passwordBuf, { name: "PBKDF2" }, false, [
    "deriveBits",
  ]);
  const hashBuf = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations: 250000, hash: "SHA-256" },
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

const password = process.argv[2];
if (!password) {
  console.log("Usage: node hash-password.mjs <your-password-here>");
  process.exit(1);
}

const hashedPassword = await hashPassword(password);
console.log("Hashed String:", hashedPassword);
