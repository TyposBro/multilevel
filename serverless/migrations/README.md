## Database Migrations (Cloudflare D1)

This project uses Wrangler's built-in migrations to manage changes to the D1 database schema. This ensures that schema changes are version-controlled, repeatable, and safe to apply across different environments (local and remote).

### Why Use Migrations?

-   **Version Control:** Database schema changes are tracked in Git alongside the application code.
-   **Consistency:** Ensures that all environments (local development, production) have the exact same database structure.
-   **Safety:** Wrangler tracks which migrations have already been applied and will only run new ones, preventing accidental re-runs.

### Creating a New Migration

When you need to change the database schema (e.g., add a new table, alter a column), you must create a new migration file. **Never edit an already-applied migration file.**

1.  **Generate a new migration file** with a descriptive name. Run this command from the `serverless` directory:
    ```bash
    npx wrangler d1 migrations create <database-name> <migration-name>
    ```
    *Example:*
    ```bash
    npx wrangler d1 migrations create multilevel-db add_user_bio_field
    ```

2.  **Edit the generated `.sql` file.** A new file will be created in the `serverless/migrations` directory. Open this file and write the SQL commands for your schema change.

    *   **Important:** If you are modifying a table with live data (e.g., adding a `NOT NULL` column or changing a constraint), you must use the safe migration pattern: `RENAME` the old table, `CREATE` the new one, `INSERT` the data from the old to the new, and finally `DROP` the old table.

### Applying Migrations

You can apply pending migrations to your local or remote database.

**1. Apply Migrations to Your Local Database**

This is for testing your changes during development.

```bash
npx wrangler d1 migrations apply multilevel-db --local
```

**2. Apply Migrations to the Production Database**

After testing locally and merging your changes, apply them to the live database.

```bash
npx wrangler d1 migrations apply multilevel-db --remote
```

Wrangler will show you which migration files it is about to apply and ask for confirmation before proceeding.
