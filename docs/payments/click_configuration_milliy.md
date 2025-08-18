# Click Configuration for Spiko (Milliy Technology)

## Your Click Account Details

Based on your merchant dashboard:

### Account Information
- **Company**: ООО "MILLIY TECHNOLOGY"
- **Service ID**: 80012
- **Service Name**: "Оплата за услуги Spiko"
- **Merchant Account**: 44439
- **Contract**: B/D 18457 (dated 30-06-2025)

### Service Configuration
- **Minimum Amount**: 1,000.00 сум
- **Maximum Amount**: 500,000,000.00 сум
- **Status**: Active ✅

### Webhook URLs (Already Configured)
- **Prepare URL**: https://typosbro-multilevel-api.milliytechnology.workers.dev/api/payment/click/webhook
- **Complete URL**: https://typosbro-multilevel-api.milliytechnology.workers.dev/api/payment/click/webhook

## Environment Variables Setup

Add these to your Cloudflare Workers environment:

```bash
# Click Configuration
CLICK_MERCHANT_ID_LIVE=44439
CLICK_SERVICE_ID_LIVE=80012
CLICK_SECRET_KEY_LIVE=your_secret_key_from_dashboard

# For testing (you can use the same values or get test credentials)
CLICK_MERCHANT_ID_TEST=44439
CLICK_SERVICE_ID_TEST=80012
CLICK_SECRET_KEY_TEST=your_test_secret_key
```

⚠️ **Important**: Get your secret key from the Click dashboard (it's hidden with dots: ············)

## Recent Activity

Based on your dashboard, I can see:
- **Recent invoices created**: Yes (16-08-2025, 13-08-2025)
- **All for 1,000 сум**: Matches your plan pricing ✅
- **Phone numbers**: 99890***1207, 99891***7000, 99891***4000
- **Status**: Some invoices are being created successfully

## Configuration Verification

### 1. Plans Configuration
Your current plan in `config/plans.js`:
```javascript
silver_monthly: {
  tier: "silver",
  durationDays: 30,
  prices: {
    uzs: 100000, // 1,000 UZS in Tiyin ✅ Matches dashboard
  },
  providerIds: {
    click: "80012", // ✅ Matches your Service ID
  },
}
```

### 2. Payment URL Format
Your users will be redirected to:
```
https://my.click.uz/services/pay?service_id=80012&merchant_id=44439&amount=1000.00&transaction_param=<transaction_id>&return_url=<return_url>
```

### 3. Webhook Testing
Test your webhook with Click's testing tool using:
- **Service ID**: 80012
- **Merchant ID**: 44439
- **Secret Key**: (from your dashboard)

## Testing Commands

### Test Payment Creation
```bash
curl -X POST https://typosbro-multilevel-api.milliytechnology.workers.dev/api/payment/create \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "provider": "click",
    "planId": "silver_monthly"
  }'
```

### Test Webhook Manually
```bash
curl -X POST https://typosbro-multilevel-api.milliytechnology.workers.dev/api/payment/click/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "click_trans_id": 12345,
    "service_id": 80012,
    "merchant_trans_id": "test_transaction",
    "amount": 1000.00,
    "action": 0,
    "error": 0,
    "error_note": "Success",
    "sign_time": "2025-08-18 12:00:00",
    "sign_string": "your_calculated_signature"
  }'
```

## Android App Configuration

Update your Android app with correct values:

```kotlin
// In PaymentModels.kt - already updated
val AVAILABLE_PLANS = listOf(
    PaymentPlan(
        id = "silver_monthly",
        name = "Серебряный план",
        priceUzs = 100000L, // 1000 UZS in tiyin
        priceSums = 1000.0,  // Matches your dashboard
        durationDays = 30,
        tier = "silver",
        description = "Доступ ко всем функциям на 1 месяц"
    )
)
```

## Troubleshooting

### Common Issues & Solutions

1. **"Service not found" errors**
   - ✅ Your Service ID (80012) is correct and active

2. **Amount validation errors**
   - ✅ Your plan (1,000 сум) is within limits (1,000 - 500,000,000)

3. **Webhook signature errors**
   - Check if secret key is correctly set in environment variables
   - Verify signature calculation in webhook logs

4. **Payment not redirecting back**
   - Check return URL format: `multilevelapp://login?payment_status=success&transaction_id=<id>`

### Recent Transaction Analysis
From your dashboard, I see invoices being created successfully:
- Multiple transactions for 1,000 сум ✅
- Different phone numbers being used ✅
- This suggests your API is working for invoice creation

## Next Steps

1. **Set Secret Key**: Get the secret key from your Click dashboard
2. **Test Signature**: Verify webhook signature calculation
3. **Test Payment Flow**: Complete end-to-end payment test
4. **Monitor Logs**: Check Cloudflare Workers logs for any errors

## Support Contacts

Based on your contract:
- **Contact Person**: Тошпулатов И.У
- **Phone**: 998901611985
- **Bank**: АО "Click"

Your integration is well-configured and appears to be working based on the recent activity in your dashboard!
