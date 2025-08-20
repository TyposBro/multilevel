# Google Play Console Configuration Guide

## 🚀 Real-time Developer Notifications Setup

### Step 1: Create Cloud Pub/Sub Topic

1. **Go to Google Cloud Console**: https://console.cloud.google.com
2. **Select your project**: `test-project` (or your actual project ID)
3. **Navigate to Pub/Sub**: Search "Pub/Sub" in the search bar
4. **Create Topic**:
   - Topic ID: `google-play-notifications`
   - Leave other settings as default
5. **Copy the full topic name**: `projects/YOUR_PROJECT_ID/topics/google-play-notifications`

### Step 2: Configure Google Play Console

**In the "Real-time developer notifications" section you're currently viewing:**

1. **Enable real-time notifications**: ✅ Check the box
2. **Topic name**: Enter `projects/YOUR_PROJECT_ID/topics/google-play-notifications`
3. **Notification content**: Select "Subscriptions, voided purchases, and all one-time products"

### Step 3: Set Up Cloud Function/Webhook Endpoint

**Option A: Direct Webhook (Recommended for your setup)**
- **Endpoint URL**: `https://your-workers-domain.workers.dev/webhooks/google-play`
- This will send notifications directly to your Cloudflare Workers

**Option B: Pub/Sub + Cloud Function (If you prefer Google Cloud)**
- Set up a Cloud Function that forwards to your Workers endpoint

## 🔑 Google Play Licensing Key

The licensing key shown in your console:
```
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqQ3aID9hune3P+x6KFaMVBvSbvYBANijPjQPpqoHhyr08PSUHm7Lod8ft2UjKoQv1i8LKbr/Kz0mfW75+1WB3FZHSItgRwZfOnUvLTaJNXxMiOjniHoFyti4WW+6Ru1+UGmwg+IxHDgP2kYbYv9NRd7RUMLwmqyLO0pp8nrsjITUuqeV1jn6LYaazr2pTPbcny3s1hFt90DFxxHnE9XRcPE+mqXLM+qV55Vl/0As3Sw4Hf+TAt8EaYCOCw+5BUiGfhGayPqJ1SihnytFua0B7jjY3OdjHnj9KcmsB3IAGcXQzT/72x7/KuoKsA9l8JyFZw5RyCoROFsjUB9B0fDtgwIDAQAB
```

**Add this to your Android app** in the billing setup to verify purchases locally.

## 📋 Environment Variables for Your Workers

Add these to your Cloudflare Workers environment:

```bash
# Google Play Console Configuration
GOOGLE_PLAY_PACKAGE_NAME="org.milliytechnology.spiko"
GOOGLE_PLAY_PUBLIC_KEY="MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqQ3aID9hune3P+x6KFaMVBvSbvYBANijPjQPpqoHhyr08PSUHm7Lod8ft2UjKoQv1i8LKbr/Kz0mfW75+1WB3FZHSItgRwZfOnUvLTaJNXxMiOjniHoFyti4WW+6Ru1+UGmwg+IxHDgP2kYbYv9NRd7RUMLwmqyLO0pp8nrsjITUuqeV1jn6LYaazr2pTPbcny3s1hFt90DFxxHnE9XRcPE+mqXLM+qV55Vl/0As3Sw4Hf+TAt8EaYCOCw+5BUiGfhGayPqJ1SihnytFua0B7jjY3OdjHnj9KcmsB3IAGcXQzT/72x7/KuoKsA9l8JyFZw5RyCoROFsjUB9B0fDtgwIDAQAB"

# Google Service Account (Create this next)
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON='{"type":"service_account","project_id":"...","private_key":"...","client_email":"..."}'
```

## 🔧 Next Immediate Actions

1. **Complete Real-time Notifications Setup** (in current screen)
2. **Create Google Service Account** (for API access)
3. **Deploy your Workers with environment variables**
4. **Test the webhook integration**
5. **Set up subscription products in Google Play Console**

## 📱 Android App Integration

Your Android app will need these in the billing integration:
- **Package name**: `org.milliytechnology.spiko`
- **Public key**: The RSA key from console
- **API endpoints**: Your Workers URLs for purchase verification

Would you like me to help you with any specific step next?
