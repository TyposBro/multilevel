// Click Web Integration Guide for Spiko

## Overview
Your Click integration implements the full web payment flow as specified in the Click documentation. Here's what's implemented and how to enhance it.

## Current Flow
1. User initiates payment → `createPayment` API
2. System generates Click payment URL → User redirected to my.click.uz
3. User completes payment on Click's site
4. Click sends webhooks (Prepare → Complete) to your server
5. System processes webhooks and updates subscription

## Implementation Files
- `/serverless/src/controllers/payments/paymentController.js` - Main webhook handler
- `/serverless/src/services/providers/clickService.js` - Click service logic
- `/serverless/src/routes/paymentRoutes.js` - Route definitions
- `/serverless/src/config/plans.js` - Plan configuration with Click service IDs

## Environment Variables Required
```
# Test Environment
CLICK_MERCHANT_ID_TEST=your_test_merchant_id
CLICK_MERCHANT_USER_ID_TEST=your_test_merchant_user_id  
CLICK_SECRET_KEY_TEST=your_test_secret_key

# Production Environment
CLICK_MERCHANT_ID_LIVE=your_live_merchant_id
CLICK_MERCHANT_USER_ID_LIVE=your_live_merchant_user_id
CLICK_SECRET_KEY_LIVE=your_live_secret_key
```

## API Endpoints
- `POST /api/payment/create` - Create payment (protected)
- `POST /api/payment/click/webhook` - Click webhook handler (public)
- `GET /api/payment/click/webhook` - Webhook test endpoint

## Frontend Integration Examples

### React/JavaScript
```javascript
// Create payment and redirect to Click
const initiateClickPayment = async (planId) => {
  try {
    const response = await fetch('/api/payment/create', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`
      },
      body: JSON.stringify({
        provider: 'click',
        planId: planId
      })
    });
    
    const result = await response.json();
    
    if (result.success) {
      // Redirect user to Click payment page
      window.location.href = result.paymentUrl;
    }
  } catch (error) {
    console.error('Payment creation failed:', error);
  }
};
```

### React Native
```javascript
// For mobile app integration
import { Linking } from 'react-native';

const initiateClickPayment = async (planId) => {
  try {
    const response = await fetch('/api/payment/create', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`
      },
      body: JSON.stringify({
        provider: 'click',
        planId: planId
      })
    });
    
    const result = await response.json();
    
    if (result.success) {
      // Open Click payment page in browser
      await Linking.openURL(result.paymentUrl);
    }
  } catch (error) {
    console.error('Payment creation failed:', error);
  }
};
```

## Testing

### Test Webhook Locally
```bash
# Test webhook accessibility
curl -X GET http://localhost:8787/api/payment/click/webhook

# Test webhook with mock data
curl -X POST http://localhost:8787/api/payment/click/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "click_trans_id": 12345,
    "service_id": 80012,
    "merchant_trans_id": "test",
    "amount": 1000.00,
    "action": 0,
    "error": 0,
    "error_note": "Success",
    "sign_time": "2025-01-01 12:00:00",
    "sign_string": "your_test_signature"
  }'
```

## Security Considerations
1. ✅ Signature verification implemented
2. ✅ Environment-specific credentials
3. ✅ Proper error handling
4. ✅ Transaction ID validation
5. ✅ Amount validation with tolerance

## Deployment Checklist
- [ ] Set production environment variables
- [ ] Configure Click merchant account with webhook URL
- [ ] Test with Click's testing tool
- [ ] Verify return URLs work correctly
- [ ] Test error scenarios
