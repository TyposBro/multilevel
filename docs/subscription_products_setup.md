# Subscription Products Setup Guide

## 📱 Creating Subscription Products in Google Play Console

### Step 1: Navigate to Subscriptions
1. **Go to**: Monetize with Play → Products
2. **Click**: "Create subscription"

### Step 2: Create Your Subscription Plans

Based on your existing code, create these subscriptions:

#### **Gold Monthly Subscription**
- **Product ID**: `gold_monthly`
- **Name**: `Gold Monthly Plan`
- **Description**: `Premium features with monthly billing`
- **Price**: Set your desired price
- **Billing period**: `1 month`
- **Free trial**: Configure if desired

#### **Gold Yearly Subscription**
- **Product ID**: `gold_yearly`
- **Name**: `Gold Yearly Plan`
- **Description**: `Premium features with yearly billing (save money!)`
- **Price**: Set your desired price (typically 10-12 months cost)
- **Billing period**: `1 year`
- **Free trial**: Configure if desired

#### **Premium Monthly Subscription**
- **Product ID**: `premium_monthly`
- **Name**: `Premium Monthly Plan`
- **Description**: `All premium features with monthly billing`
- **Price**: Set your desired price
- **Billing period**: `1 month`

### Step 3: Create One-time Products (if needed)

#### **Remove Ads**
- **Product ID**: `remove_ads`
- **Name**: `Remove Ads`
- **Description**: `Remove all advertisements permanently`
- **Price**: Set one-time price

### Step 4: Activate Products
1. **Review all settings**
2. **Click "Activate" for each product**
3. **Wait for Google Play approval** (can take several hours)

### Step 5: Test with License Testers
1. **Go to**: Test and release → License testing
2. **Add test accounts**: Add your email and team emails
3. **Test subscriptions**: Use test accounts to verify billing flow

## 🔧 Integration with Your App

### Android App Configuration
```xml
<!-- In your app's build.gradle -->
dependencies {
    implementation 'com.android.billingclient:billing:6.1.0'
}
```

### Product IDs in Code
Make sure your Android app uses these exact product IDs:
- `gold_monthly`
- `gold_yearly` 
- `premium_monthly`
- `remove_ads`

### API Endpoints
Your Android app should call these endpoints after purchase:
- **Subscription verification**: `POST /api/subscriptions/verify-google-play`
- **Product verification**: `POST /api/subscriptions/verify-google-play-product`
- **Status check**: `GET /api/subscriptions/google-play-status`

## 🚀 Go Live Checklist

- [ ] Real-time notifications configured
- [ ] Service account created and configured
- [ ] Subscription products created and activated
- [ ] Products tested with license testers
- [ ] Android app integrated with billing
- [ ] Serverless backend deployed with environment variables
- [ ] Webhook endpoint tested

## 🔍 Testing Process

1. **Add test account** to License testing
2. **Install your app** on test device
3. **Attempt purchase** with test account
4. **Verify webhook** receives notification
5. **Check database** for transaction records
6. **Test subscription features** in app

Your Google Play Billing is ready for production once all checklist items are complete!
