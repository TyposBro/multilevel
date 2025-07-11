# /src/scripts/ - Administrative Tasks


## How to Create a New Admin User

1. **Generate a Password Hash:**
    Since the hashing now uses the Web Crypto API, you can generate a hash using a simple Deno script, a browser's developer console, or a simple local Node script.

    * Run the script (Node.js v18+ supports `crypto` globally):

        ```bash
        node ./serverless/src/scripts/hash-password.mjs 'a-very-secure-password'
      ```

    * **Copy the output hash string.** It will look something like `a1b2c3...:d4e5f6...`.

2. **Execute the SQL Command:**
    Use `wrangler` to insert the new admin into your `admins` table.

    ```bash
    npx wrangler d1 execute <your-db-name> --command="INSERT INTO admins (id, email, password) VALUES ('uuid-here', 'admin@example.com', 'paste-your-salt:hash-string-here');"
    ```
