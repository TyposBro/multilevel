# Google Play Billing Integration Guide

This document explains how to use, test, and maintain the Google Play Billing implementation in the Spiko Android app.

## 📋 Overview

The app uses Google Play Billing Library 8.0.0 with a clean architecture that separates concerns:

- **BillingClientWrapper**: Interface for billing operations
- **BillingClientWrapperImpl**: Real implementation for production
- **FakeBillingClientWrapper**: Test implementation for debug builds
- **BillingTestHelper**: Testing utilities for debug builds

## 🏗️ Architecture

```
UI Layer (SubscriptionScreen)
    ↓
ViewModel (SubscriptionViewModel)
    ↓
Repository (SubscriptionRepository)
    ↓
BillingClientWrapper
    ↓
Google Play Billing / FakeBillingClient
```

## 🔧 Configuration

### 1. Product IDs
The app currently supports these subscription products:
- `silver_monthly` - Silver tier monthly subscription
- `gold_monthly` - Gold tier monthly subscription

### 2. Build Variants
- **Debug builds**: Use `FakeBillingClientWrapper` for testing
- **Release builds**: Use `BillingClientWrapperImpl` for real purchases

### 3. Permissions
Required permissions are already configured in `AndroidManifest.xml`:
```xml
<uses-permission android:name="com.android.vending.billing" />
```

## 🧪 Testing

### Debug Testing (Recommended)

1. **Use Debug Builds**: Debug builds automatically use the fake billing client
2. **Access Debug Screen**: Add navigation to `BillingDebugScreen` in your debug builds
3. **Test Scenarios**: Use the debug screen to simulate different billing scenarios

```kotlin
// Example: Add to your debug navigation
composable("billing_debug") {
    BillingDebugScreen(onNavigateBack = { navController.popBackStack() })
}
```

### Unit Tests

Run billing unit tests:
```bash
./gradlew testBilling
```

### Integration Tests

Run integration tests on connected device:
```bash
./gradlew testBillingIntegration
```

### Complete Test Suite

Run all billing tests:
```bash
./gradlew testBillingFull
```

## 📱 Testing on Real Devices

### Google Play Console Setup

1. **Upload APK**: Upload a signed APK to Google Play Console
2. **Create Products**: Create subscription products with IDs matching your app
3. **Add Test Accounts**: Add test accounts for purchase testing
4. **Enable Testing**: Use Google Play Console testing tracks

### Test Account Setup

1. Go to Google Play Console → Your App → Monetize → Products → Subscriptions
2. Add test accounts in the "License Testing" section
3. Install the app via Google Play Store (not direct APK install)

### Testing Real Purchases

```kotlin
// In release builds, this will trigger real Google Play Billing
viewModel.launchGooglePlayPurchase(activity, productDetails)
```

## 🐛 Debugging

### Common Issues

1. **Billing Unavailable**
   - Ensure app is installed via Google Play Store
   - Check Google Play Services is updated
   - Verify test account is added to Google Play Console

2. **Product Not Found**
   - Verify product IDs match Google Play Console
   - Ensure products are active in console
   - Check app version matches uploaded APK

3. **Purchase Already Owned**
   - Use Google Play Console to cancel test subscriptions
   - Or use different test accounts

### Debug Logging

All billing operations are logged with tag `"BillingClient"`. Filter logs:
```bash
adb logcat | grep BillingClient
```

### Debug Screen Features

The `BillingDebugScreen` provides:
- Real-time billing status
- Manual product queries
- Purchase simulation
- State reset
- Live logging

## 🔄 Purchase Flow

### 1. Initialization
```kotlin
// ViewModel initializes billing client
billingClient.startConnection()
```

### 2. Product Query
```kotlin
// Query available products
billingClient.queryProductDetails(listOf("silver_monthly", "gold_monthly"))
```

### 3. Purchase Launch
```kotlin
// Launch purchase flow
billingClient.launchPurchaseFlow(activity, productDetails)
```

### 4. Purchase Verification
```kotlin
// Verify with backend and acknowledge
verifyAndAcknowledgePurchase(provider, token, planId, purchase)
```

## 🔒 Security

### Backend Verification

All purchases are verified with your backend:
```kotlin
// Purchase token is sent to backend for verification
subscriptionRepository.verifyPurchase("google", purchaseToken, planId)
```

### Service Account

Ensure your backend has proper Google Service Account credentials for verification.

## 📈 Monitoring

### Key Metrics to Track

1. **Connection Success Rate**: `isReady` state changes
2. **Purchase Success Rate**: Successful vs failed purchases
3. **Verification Success Rate**: Backend verification results
4. **Error Frequencies**: Different billing error codes

### Analytics Events

Consider tracking these events:
- Billing client connected
- Product details loaded
- Purchase initiated
- Purchase completed
- Purchase verified
- Billing errors

## 🚀 Deployment Checklist

### Before Release

- [ ] Test with real Google Play Console setup
- [ ] Verify all product IDs exist and are active
- [ ] Test with multiple test accounts
- [ ] Verify backend verification works
- [ ] Test purchase restoration
- [ ] Test network error scenarios
- [ ] Verify analytics tracking

### Release Configuration

- [ ] Use release build (real billing client)
- [ ] Ensure signed APK uploaded to Play Console
- [ ] Products are active in Play Console
- [ ] Test accounts configured
- [ ] Backend service account configured

## 🆘 Troubleshooting

### Debug Mode Issues

If debug testing isn't working:
1. Verify you're using a debug build
2. Check `BillingModule` in debug source set
3. Confirm `FakeBillingClientWrapper` is being injected

### Production Issues

If real purchases aren't working:
1. Verify app is installed via Play Store
2. Check Google Play Console product setup
3. Verify backend service account credentials
4. Check test account configuration

### Getting Help

1. Check Android logs: `adb logcat | grep BillingClient`
2. Use the debug screen for real-time status
3. Review Google Play Console for account/product issues
4. Test with known working test accounts

## 📚 Additional Resources

- [Google Play Billing Documentation](https://developer.android.com/google/play/billing)
- [Testing Google Play Billing](https://developer.android.com/google/play/billing/test)
- [Google Play Console Help](https://support.google.com/googleplay/android-developer)
