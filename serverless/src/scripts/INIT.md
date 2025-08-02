# --- JWT Secrets ---
npx wrangler secret put JWT_SECRET
# Paste your '123456' or a new, strong secret

npx wrangler secret put JWT_SECRET_ADMIN
# Paste your admin JWT secret

# --- API Keys ---
npx wrangler secret put GEMINI_API_KEY
npx wrangler secret put OPENAI_API_KEY

# --- Telegram Bot ---
npx wrangler secret put TELEGRAM_BOT_TOKEN

# --- Payme Credentials ---
# Live
npx wrangler secret put PAYME_MERCHANT_ID_LIVE
npx wrangler secret put PAYME_SECRET_KEY_LIVE
# Test
npx wrangler secret put PAYME_MERCHANT_ID_TEST
npx wrangler secret put PAYME_SECRET_KEY_TEST

# --- Click Credentials ---
# Test
npx wrangler secret put CLICK_MERCHANT_ID_TEST
npx wrangler secret put CLICK_MERCHANT_USER_ID_TEST
npx wrangler secret put CLICK_SERVICE_ID_TEST
npx wrangler secret put CLICK_SECRET_KEY_TEST
# Live
npx wrangler secret put CLICK_MERCHANT_ID_LIVE
npx wrangler secret put CLICK_MERCHANT_USER_ID_LIVE
npx wrangler secret put CLICK_SERVICE_ID_LIVE
npx wrangler secret put CLICK_SECRET_KEY_LIVE

# --- Google Play Billing ---
npx wrangler secret put GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
# (Go to Google Cloud Console -> IAM & Admin -> Service Accounts -> Create Key -> JSON)
# (Paste the entire single-line JSON blob)


# --- DEPRECATED/REPLACED Secrets ---
# The following credentials are for Node.js SDKs that don't work in Workers.
# The functionality has been replaced by Cloudflare R2 and D1.

# If you were to use Supabase, the KEY would be a secret:
# npx wrangler secret put SUPABASE_KEY

# For Firebase, you would store the entire JSON content as a secret:
# npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON
# (Then paste the entire single-line JSON blob)