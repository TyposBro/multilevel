# Click Secret Key Configuration Guide

## 🔐 Setting Up Your Click Secret Key

Your Click integration is working (you have real transactions!), but the webhook signature validation is failing because the secret key is not configured.

### 1. Get Your Secret Key from Click Dashboard

1. **Login to Click Merchant Dashboard**: https://checkout.click.uz/
2. **Navigate to Services**: Go to your service with ID `80012`
3. **Find Secret Key**: Look for "Секретный ключ" or "Secret Key"
   - It might be displayed as dots: `············`
   - Or you might need to click "Show" or "Reveal"

### 2. Configure Environment Variables

For **Cloudflare Workers** (your current setup), you need to set these secrets:

```bash
# Production environment
npx wrangler secret put CLICK_SECRET_KEY_LIVE
# When prompted, enter your actual secret key

# Test environment (if you have a test merchant account)
npx wrangler secret put CLICK_SECRET_KEY_TEST
```

### 3. Alternative: Manual Configuration via Cloudflare Dashboard

1. Go to **Cloudflare Dashboard** → **Workers & Pages**
2. Select your worker: `typosbro-multilevel-api`
3. Go to **Settings** → **Variables**
4. Add new environment variable:
   - Name: `CLICK_SECRET_KEY_LIVE`
   - Value: Your actual secret key from Click dashboard
   - Type: **Encrypted** (recommended)

### 4. Test the Configuration

After setting the secret key, run this test:

```bash
cd /home/ched54/Documents/milliy/spiko
node serverless/calculate-click-signature.js
```

Update the script with your actual secret key to test signature generation.

### 5. Verify Integration

Test webhook with real signature:

```bash
# Replace YOUR_SECRET_KEY with actual key
curl -X POST https://typosbro-multilevel-api.milliytechnology.workers.dev/api/payment/click/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "click_trans_id": 12345,
    "service_id": "80012",
    "merchant_trans_id": "test_123",
    "merchant_prepare_id": "",
    "amount": "1000.00",
    "action": 0,
    "sign_time": "2025-08-18 12:00:00",
    "error": 0,
    "error_note": "Success",
    "sign_string": "[CALCULATED_MD5_SIGNATURE]"
  }'
```

## 🔍 Current Status Summary

✅ **Working Components:**
- Click service is ACTIVE (Service ID: 80012)
- Payment URLs are generating correctly
- Real transactions are being processed (1,000 сум payments on 16-08 and 13-08)
- Webhook endpoint is accessible
- Android UI components are implemented

❌ **Missing Component:**
- Secret key for signature verification

## 📝 Secret Key Location in Dashboard

The secret key is typically found in one of these locations:

1. **Service Settings** → **Security** → **Secret Key**
2. **API Settings** → **Webhook Configuration** → **Secret Key**  
3. **Integration** → **Authentication** → **Secret Key**

## 🚨 Important Security Notes

1. **Never commit the secret key to version control**
2. **Use environment variables or encrypted secrets**
3. **Different keys for test/production environments**
4. **Rotate keys periodically for security**

## 🎯 Next Steps

1. **Find your secret key** in the Click merchant dashboard
2. **Set the environment variable** using `wrangler secret put`
3. **Test signature generation** using the calculator script
4. **Verify webhook processing** with real signatures
5. **Monitor dashboard** for successful webhook confirmations

Once the secret key is configured, your integration will be 100% functional! 🎉

---

**Need Help?**
- Contact Click support if you can't find the secret key
- Check your merchant contract documents
- Ask your account manager for the API credentials
