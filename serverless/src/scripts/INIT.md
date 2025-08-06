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
(This is often the same as the merchant ID, but confirm with Click)


npx wrangler secret put CLICK_SECRET_KEY_TEST 
# Paste your redacted secret key here

# Live
npx wrangler secret put CLICK_MERCHANT_ID_LIVE
npx wrangler secret put CLICK_MERCHANT_USER_ID_LIVE
npx wrangler secret put CLICK_SERVICE_ID_LIVE
npx wrangler secret put CLICK_SECRET_KEY_LIVE


# --- Google Play Billing ---
npx wrangler secret put GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
# (Go to Google Cloud Console -> IAM & Admin -> Service Accounts -> Create Key -> JSON)
# (Paste the entire single-line JSON blob)