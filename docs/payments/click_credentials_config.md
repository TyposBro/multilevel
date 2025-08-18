# Click Configuration - Based on Your Dashboard

## Environment Variables Configuration

### Production (Live) Environment
```bash
# Your actual production credentials from the dashboard
CLICK_MERCHANT_ID_LIVE=44439
CLICK_MERCHANT_USER_ID_LIVE=61733  # or 62195 for SpikoPrivate user
CLICK_SERVICE_ID_LIVE=80012
CLICK_SECRET_KEY_LIVE=your_secret_key_here  # Click the dots in dashboard to reveal

# Your production webhook URL (already configured in dashboard)
CLICK_WEBHOOK_URL_LIVE=https://typosbro-multilevel-api.milliytechnology.workers.dev/api/payment/click/webhook
```

### Test Environment (if you have separate test credentials)
```bash
# If you have test credentials, use them here
CLICK_MERCHANT_ID_TEST=your_test_merchant_id
CLICK_MERCHANT_USER_ID_TEST=your_test_user_id
CLICK_SERVICE_ID_TEST=your_test_service_id
CLICK_SECRET_KEY_TEST=your_test_secret_key

# Test webhook URL
CLICK_WEBHOOK_URL_TEST=https://typosbro-multilevel-api.milliytechnology.workers.dev/api/payment/click/webhook
```

## How to Get the Secret Key

1. **In your Click dashboard**, find the service row with ID `80012`
2. **Look at the "Секретный ключ (СК)" column**
3. **Click on the dots** (`············`) to reveal the actual key
4. **Copy the revealed secret key**

## Configuration in Your Code

### Backend Configuration (serverless/src/config)
```javascript
// In your environment variables or config
const CLICK_CONFIG = {
  production: {
    merchantId: 44439,
    merchantUserId: 61733, // or 62195
    serviceId: 80012,
    secretKey: process.env.CLICK_SECRET_KEY_LIVE
  },
  test: {
    merchantId: process.env.CLICK_MERCHANT_ID_TEST,
    merchantUserId: process.env.CLICK_MERCHANT_USER_ID_TEST,
    serviceId: process.env.CLICK_SERVICE_ID_TEST,
    secretKey: process.env.CLICK_SECRET_KEY_TEST
  }
};
```

### Android Configuration
```kotlin
// In your plans.js or configuration
const PLANS = {
  silver_monthly: {
    tier: "silver",
    durationDays: 30,
    prices: {
      uzs: 100000, // 1,000 UZS in tiyin (matches your min amount)
    },
    providerIds: {
      click: "80012", // Your service ID
    },
  },
};
```

## Verification Steps

### 1. Test Your Webhook
```bash
# Test if your webhook is accessible
curl -X GET https://typosbro-multilevel-api.milliytechnology.workers.dev/api/payment/click/webhook

# Should return webhook test response
```

### 2. Verify Service Configuration
- ✅ Service ID: `80012` - Active
- ✅ Min Amount: 1,000 UZS (matches your plan)
- ✅ Max Amount: 500,000,000 UZS
- ✅ Webhook URLs: Configured correctly

### 3. Test Payment Flow
1. Create a test payment with amount `1000.00` (1,000 UZS)
2. Use phone number format: `+998XXXXXXXXX`
3. Check webhook receives proper requests

## Important Notes

### Security
- **Never commit** the secret key to version control
- **Use environment variables** for all credentials
- **Keep secret key secure** - it's used for signature verification

### Testing
- Your service is already **"Активен"** (Active)
- Minimum amount is **1,000 UZS** (perfect for testing)
- Webhook URLs are properly configured

### Multiple Users
You have two admin users:
- `spiko` (ID: 61733) - seems to be the main account
- `SpikoPrivate` (ID: 62195) - with phone number

Choose one for your integration (probably use `61733` for the main user).

## Next Steps

1. **Reveal and copy the secret key** from dashboard
2. **Set environment variables** in your serverless deployment
3. **Update your plans.js** with service ID `80012`
4. **Test the integration** with a small amount (1,000 UZS)

Would you like me to help you update your configuration files once you have the secret key?
