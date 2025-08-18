# Click Payment Integration - Android Kotlin Jetpack Compose

This document describes the Android implementation of Click payment integration for the Spiko app.

## Architecture Overview

The Click payment integration follows MVVM architecture with the following components:

### Data Layer
- **PaymentModels.kt** - Data models for API requests/responses
- **PaymentRepository.kt** - Repository for payment API calls
- **ApiService.kt** - Retrofit interface for network calls

### Domain Layer
- **ClickPaymentService.kt** - Business logic for Click payments
- **PaymentUtils.kt** - Utility functions for formatting and validation

### Presentation Layer
- **PaymentViewModel.kt** - ViewModel managing payment state
- **ClickPaymentScreen.kt** - Main payment UI screen
- **PaymentComponents.kt** - Reusable UI components

## Features

### ✅ Implemented Features
1. **Plan Selection** - Choose from available subscription plans
2. **Payment Method Selection** - Web redirect or SMS invoice
3. **Web Payment Flow** - Opens Click website in Chrome Custom Tabs
4. **SMS Invoice Flow** - Creates SMS invoice via API
5. **Phone Number Validation** - Validates Uzbekistan phone numbers
6. **Payment Status Polling** - Real-time status updates for invoices
7. **Deep Link Handling** - Handles return from Click payment
8. **Error Handling** - Comprehensive error states
9. **Loading States** - UI feedback during operations

### 🔄 Payment Flow Types

#### 1. Web Redirect Flow
```kotlin
// User selects plan and web payment method
// App creates payment via API
// App opens Click payment URL in Chrome Custom Tabs
// User completes payment on Click website
// Click redirects back to app with deep link
// App checks payment status and updates subscription
```

#### 2. SMS Invoice Flow
```kotlin
// User selects plan and invoice method
// User enters phone number
// App validates phone number
// App creates invoice via API
// Click sends SMS to user
// User pays via SMS/app
// App polls payment status
// App updates subscription when completed
```

## API Integration

### Endpoints Used
- `POST /api/payment/create` - Create payment
- `GET /api/payment/status/{transactionId}` - Check payment status

### Request/Response Models

#### Create Payment Request
```kotlin
data class CreatePaymentRequest(
    val provider: String, // "click"
    val planId: String, // "silver_monthly"
    val paymentMethod: String? = null, // "web" or "invoice"
    val phoneNumber: String? = null // Required for invoice
)
```

#### Create Payment Response
```kotlin
data class CreatePaymentResponse(
    val success: Boolean,
    val provider: String,
    val paymentMethod: String?,
    val message: String?,
    val paymentUrl: String?, // For web flow
    val transactionId: String?,
    val invoiceId: String? // For invoice flow
)
```

## UI Components

### Main Screen Components
1. **Plan Selection Card** - Shows available plans with pricing
2. **Payment Method Selector** - Web vs SMS invoice options
3. **Phone Input** - Appears for SMS invoice method
4. **Payment Button** - Triggers payment creation
5. **Status Cards** - Shows progress and results

### Navigation
The payment screen integrates with your existing navigation system:

```kotlin
// Add to your navigation graph
composable("click_payment") {
    ClickPaymentScreen(
        onNavigateBack = { navController.popBackStack() },
        onPaymentSuccess = { 
            // Navigate to success screen or main app
            navController.navigate("subscription_success")
        }
    )
}
```

## Configuration

### Deep Link Handling
Already configured in your `AndroidManifest.xml`:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.browsable" />
    <data android:host="login" android:scheme="multilevelapp" />
</intent-filter>
```

### Dependencies
Required dependencies are already in your `build.gradle.kts`:
- `androidx.browser` - Chrome Custom Tabs
- `androidx.hilt.navigation.compose` - Hilt integration
- Retrofit and other networking libraries

## Testing

### Unit Tests
Run the ClickPaymentService tests:
```bash
./gradlew testDebugUnitTest --tests="*ClickPaymentServiceTest"
```

### Manual Testing
1. **Web Flow Testing**
   - Select a plan and web payment method
   - Verify Chrome Custom Tabs opens
   - Complete payment on Click website
   - Verify deep link return works

2. **Invoice Flow Testing**
   - Select invoice method
   - Enter valid Uzbekistan phone number
   - Verify SMS is received
   - Complete payment via SMS/Click app
   - Verify status polling works

## Error Handling

The implementation handles various error scenarios:

1. **Network Errors** - Connection issues, timeouts
2. **Validation Errors** - Invalid phone numbers, missing data
3. **API Errors** - Backend errors, invalid responses
4. **Payment Errors** - Failed payments, cancelled transactions

## Security Considerations

1. **Phone Number Validation** - Prevents invalid numbers
2. **Deep Link Validation** - Validates incoming deep links
3. **API Security** - Uses authenticated requests
4. **No Sensitive Data Storage** - Doesn't store payment info locally

## Usage Example

### Adding Payment to Your Screen
```kotlin
@Composable
fun SubscriptionScreen() {
    // Your existing subscription UI
    
    // Add payment summary card
    PaymentSummaryCard(
        plan = PaymentPlan.AVAILABLE_PLANS.first(),
        onClickPayClick = {
            // Navigate to payment screen
            navController.navigate("click_payment")
        }
    )
}
```

### Handling Payment Results
```kotlin
// In your success screen or main screen
LaunchedEffect(Unit) {
    // Check if user just completed payment
    // Refresh user profile to get updated subscription
    profileViewModel.fetchUserProfile()
}
```

## Troubleshooting

### Common Issues
1. **Deep links not working** - Check AndroidManifest.xml configuration
2. **Chrome Custom Tabs not opening** - Check browser dependency
3. **Phone validation failing** - Check phone number format
4. **Payment status not updating** - Check network connectivity

### Debug Tips
1. Enable logging in PaymentViewModel
2. Check API responses in network inspector
3. Verify deep link URLs in logs
4. Test with Click's testing tools

## Future Enhancements

Potential improvements:
1. **Payment History** - Show past transactions
2. **Multiple Plans** - Support for different subscription tiers
3. **Promo Codes** - Discount code support
4. **Retry Logic** - Automatic retry for failed payments
5. **Offline Support** - Handle offline scenarios

## Support

For issues with Click integration:
1. Check Click documentation
2. Verify webhook configuration
3. Test with Click's sandbox environment
4. Contact Click support if needed
