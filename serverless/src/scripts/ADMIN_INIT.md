# /src/scripts/ - Administrative Tasks

In a Cloudflare Worker environment, we don't run local Node.js scripts to manipulate a remote database. Instead, we use the `wrangler` command-line tool to execute SQL commands directly against our D1 database.

## How to Create a New Admin User

1. **Hash the Password:**
    First, you need to generate a secure hash for the admin's password. You can do this with a simple local Node.js script (since this part doesn't need to run on Cloudflare).

    * Create a temporary file `hash-password.js`:

        ```javascript
        const bcrypt = require('bcryptjs');
        const password = process.argv[2];
        if (!password) {
          console.log('Usage: node hash-password.js <your-password-here>');
          process.exit(1);
        }
        const salt = bcrypt.genSaltSync(10);
        const hash = bcrypt.hashSync(password, salt);
        console.log('Hashed Password:', hash);
      ```

    * Install bcrypt: `npm install bcryptjs`
    * Run the script:

        ```bash
        node hash-password.js 'a-very-secure-password'
        ```

    * **Copy the output hash.** It will look something like `$2a$10$AbC...`.

2. **Execute the SQL Command:**
    Use the `wrangler d1 execute` command to insert the new admin into your `admins` table. Replace `<your-db-name>`, `<email>`, and `<hashed-password>` with your actual values.

    ```bash
    npx wrangler d1 execute <your-db-name> --command="INSERT INTO admins (id, email, password) VALUES ('your-uuid-here', '<email>', '<hashed-password>');"
    ```

    **Example:**

    ```bash
    npx wrangler d1 execute typosbro-db --command="INSERT INTO admins (id, email, password) VALUES ('a1b2c3d4-e5f6-7890-1234-567890abcdef', 'admin@example.com', '$2a$10$AbC...');"
    ```

    *You can generate a UUID for the `id` from any online generator.*

This method is secure, repeatable, and aligns with the best practices for managing serverless databases.
