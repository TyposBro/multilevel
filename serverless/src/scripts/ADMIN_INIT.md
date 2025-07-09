# /src/scripts/ - Administrative Tasks

...

## How to Create a New Admin User

1.  **Generate a Password Hash:**
    Since the hashing now uses the Web Crypto API, you can generate a hash using a simple Deno script, a browser's developer console, or a simple local Node script.

    *   Create a temporary file `hash-password.mjs`:
        ```javascript
        // hash-password.mjs
        async function hashPassword(password) {
          const passwordBuf = new TextEncoder().encode(password);
          const salt = crypto.getRandomValues(new Uint8Array(16));
          const key = await crypto.subtle.importKey('raw', passwordBuf, { name: 'PBKDF2' }, false, ['deriveBits']);
          const hashBuf = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt, iterations: 250000, hash: 'SHA-256' }, key, 256);
          const hash = new Uint8Array(hashBuf);
          const saltHex = Array.from(salt).map(b => b.toString(16).padStart(2, '0')).join('');
          const hashHex = Array.from(hash).map(b => b.toString(16).padStart(2, '0')).join('');
          return `${saltHex}:${hashHex}`;
        }

        const password = process.argv[2];
        if (!password) {
          console.log('Usage: node hash-password.mjs <your-password-here>');
          process.exit(1);
        }

        const hashedPassword = await hashPassword(password);
        console.log('Hashed String:', hashedPassword);
        ```
    *   Run the script (Node.js v18+ supports `crypto` globally):
        ```bash
        node hash-password.mjs 'a-very-secure-password'
        ```
    *   **Copy the output hash string.** It will look something like `a1b2c3...:d4e5f6...`.

2.  **Execute the SQL Command:**
    Use `wrangler` to insert the new admin into your `admins` table.

    ```bash
    npx wrangler d1 execute <your-db-name> --command="INSERT INTO admins (id, email, password) VALUES ('uuid-here', 'admin@example.com', 'paste-your-salt:hash-string-here');"
    ```