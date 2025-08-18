// Test Click integration with curl commands and JavaScript examples

## Test Click Payment Integration

### Environment Setup
First, ensure your environment variables are set:

```bash
# Add to your .env file or environment
CLICK_MERCHANT_ID_TEST=your_test_merchant_id
CLICK_MERCHANT_USER_ID_TEST=your_test_merchant_user_id
CLICK_SECRET_KEY_TEST=your_test_secret_key

# For production
CLICK_MERCHANT_ID_LIVE=your_live_merchant_id
CLICK_MERCHANT_USER_ID_LIVE=your_live_merchant_user_id
CLICK_SECRET_KEY_LIVE=your_live_secret_key
```

### 1. Test Payment Creation (Web Flow)

```bash
# Create a Click payment (web redirect)
curl -X POST http://localhost:8787/api/payment/create \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_AUTH_TOKEN" \
  -d '{
    "provider": "click",
    "planId": "silver_monthly"
  }'

# Expected response:
# {
#   "success": true,
#   "provider": "click",
#   "paymentMethod": "web",
#   "paymentUrl": "https://my.click.uz/services/pay?service_id=80012&merchant_id=...",
#   "transactionId": "internal_transaction_id",
#   "clickTransactionId": "click_transaction_id",
#   "message": "Redirect to Click payment page"
# }
```

### 2. Test Invoice Creation

```bash
# Create a Click invoice
curl -X POST http://localhost:8787/api/payment/create \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_AUTH_TOKEN" \
  -d '{
    "provider": "click",
    "planId": "silver_monthly",
    "paymentMethod": "invoice",
    "phoneNumber": "+998901234567"
  }'

# Expected response:
# {
#   "success": true,
#   "provider": "click",
#   "paymentMethod": "invoice",
#   "invoiceId": "click_invoice_id",
#   "transactionId": "internal_transaction_id",
#   "clickTransactionId": "click_transaction_id",
#   "message": "Invoice created successfully. Check your SMS for payment instructions."
# }
```

### 3. Test Webhook Endpoints

```bash
# Test webhook accessibility
curl -X GET http://localhost:8787/api/payment/click/webhook

# Test webhook with mock PREPARE data
curl -X POST http://localhost:8787/api/payment/click/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "click_trans_id": 12345,
    "service_id": 80012,
    "merchant_trans_id": "test_transaction_123",
    "amount": 1000.00,
    "action": 0,
    "error": 0,
    "error_note": "Success",
    "sign_time": "2025-01-01 12:00:00",
    "sign_string": "calculated_md5_signature"
  }'

# Test webhook with mock COMPLETE data
curl -X POST http://localhost:8787/api/payment/click/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "click_trans_id": 12345,
    "service_id": 80012,
    "merchant_trans_id": "test_transaction_123",
    "merchant_prepare_id": "test_transaction_123",
    "amount": 1000.00,
    "action": 1,
    "error": 0,
    "error_note": "Success",
    "sign_time": "2025-01-01 12:00:00",
    "sign_string": "calculated_md5_signature"
  }'
```

### 4. Check Payment Status

```bash
# Check payment status
curl -X GET http://localhost:8787/api/payment/status/transaction_id_here \
  -H "Authorization: Bearer YOUR_AUTH_TOKEN"

# Expected response:
# {
#   "transactionId": "internal_transaction_id",
#   "status": "PENDING|PREPARED|COMPLETED|FAILED",
#   "provider": "click",
#   "planId": "silver_monthly",
#   "amount": 100000,
#   "createdAt": "2025-01-01T12:00:00.000Z",
#   "externalStatus": { /* Click API response */ }
# }
```

### 5. Frontend Integration Examples

#### React Payment Button

```jsx
import React, { useState } from 'react';

const ClickPaymentButton = ({ planId, authToken }) => {
  const [loading, setLoading] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState('web');
  const [phoneNumber, setPhoneNumber] = useState('');

  const handlePayment = async () => {
    setLoading(true);
    
    try {
      const payload = {
        provider: 'click',
        planId: planId
      };

      if (paymentMethod === 'invoice') {
        payload.paymentMethod = 'invoice';
        payload.phoneNumber = phoneNumber;
      }

      const response = await fetch('/api/payment/create', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${authToken}`
        },
        body: JSON.stringify(payload)
      });

      const result = await response.json();

      if (result.success) {
        if (result.paymentMethod === 'web') {
          // Redirect to Click payment page
          window.location.href = result.paymentUrl;
        } else if (result.paymentMethod === 'invoice') {
          // Show success message for invoice
          alert(result.message);
          // Optionally, start polling for payment status
          pollPaymentStatus(result.transactionId);
        }
      } else {
        alert('Payment creation failed: ' + result.message);
      }
    } catch (error) {
      console.error('Payment error:', error);
      alert('Payment creation failed');
    } finally {
      setLoading(false);
    }
  };

  const pollPaymentStatus = async (transactionId) => {
    const interval = setInterval(async () => {
      try {
        const response = await fetch(`/api/payment/status/${transactionId}`, {
          headers: {
            'Authorization': `Bearer ${authToken}`
          }
        });
        
        const status = await response.json();
        
        if (status.status === 'COMPLETED') {
          clearInterval(interval);
          alert('Payment completed successfully!');
          // Refresh page or update UI
          window.location.reload();
        } else if (status.status === 'FAILED') {
          clearInterval(interval);
          alert('Payment failed');
        }
      } catch (error) {
        console.error('Status check error:', error);
      }
    }, 5000); // Check every 5 seconds

    // Stop polling after 5 minutes
    setTimeout(() => clearInterval(interval), 300000);
  };

  return (
    <div className="payment-section">
      <h3>Pay with Click</h3>
      
      <div className="payment-method-selector">
        <label>
          <input
            type="radio"
            value="web"
            checked={paymentMethod === 'web'}
            onChange={(e) => setPaymentMethod(e.target.value)}
          />
          Web Payment (Redirect)
        </label>
        
        <label>
          <input
            type="radio"
            value="invoice"
            checked={paymentMethod === 'invoice'}
            onChange={(e) => setPaymentMethod(e.target.value)}
          />
          SMS Invoice
        </label>
      </div>

      {paymentMethod === 'invoice' && (
        <div className="phone-input">
          <input
            type="tel"
            placeholder="+998901234567"
            value={phoneNumber}
            onChange={(e) => setPhoneNumber(e.target.value)}
            required
          />
        </div>
      )}

      <button 
        onClick={handlePayment} 
        disabled={loading || (paymentMethod === 'invoice' && !phoneNumber)}
        className="click-payment-btn"
      >
        {loading ? 'Creating Payment...' : 'Pay with Click'}
      </button>
    </div>
  );
};

export default ClickPaymentButton;
```

#### React Native Implementation

```jsx
import React, { useState } from 'react';
import { View, Text, TouchableOpacity, TextInput, Alert, Linking } from 'react-native';

const ClickPaymentComponent = ({ planId, authToken }) => {
  const [loading, setLoading] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState('web');
  const [phoneNumber, setPhoneNumber] = useState('');

  const handlePayment = async () => {
    setLoading(true);
    
    try {
      const payload = {
        provider: 'click',
        planId: planId
      };

      if (paymentMethod === 'invoice') {
        payload.paymentMethod = 'invoice';
        payload.phoneNumber = phoneNumber;
      }

      const response = await fetch('/api/payment/create', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${authToken}`
        },
        body: JSON.stringify(payload)
      });

      const result = await response.json();

      if (result.success) {
        if (result.paymentMethod === 'web') {
          // Open Click payment page in browser
          await Linking.openURL(result.paymentUrl);
        } else if (result.paymentMethod === 'invoice') {
          Alert.alert('Success', result.message);
          // Start polling for payment status
          pollPaymentStatus(result.transactionId);
        }
      } else {
        Alert.alert('Error', result.message);
      }
    } catch (error) {
      console.error('Payment error:', error);
      Alert.alert('Error', 'Payment creation failed');
    } finally {
      setLoading(false);
    }
  };

  const pollPaymentStatus = async (transactionId) => {
    const interval = setInterval(async () => {
      try {
        const response = await fetch(`/api/payment/status/${transactionId}`, {
          headers: {
            'Authorization': `Bearer ${authToken}`
          }
        });
        
        const status = await response.json();
        
        if (status.status === 'COMPLETED') {
          clearInterval(interval);
          Alert.alert('Success', 'Payment completed successfully!');
        } else if (status.status === 'FAILED') {
          clearInterval(interval);
          Alert.alert('Error', 'Payment failed');
        }
      } catch (error) {
        console.error('Status check error:', error);
      }
    }, 5000);

    // Stop polling after 5 minutes
    setTimeout(() => clearInterval(interval), 300000);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Pay with Click</Text>
      
      <View style={styles.methodSelector}>
        <TouchableOpacity 
          style={[styles.methodButton, paymentMethod === 'web' && styles.selected]}
          onPress={() => setPaymentMethod('web')}
        >
          <Text>Web Payment</Text>
        </TouchableOpacity>
        
        <TouchableOpacity 
          style={[styles.methodButton, paymentMethod === 'invoice' && styles.selected]}
          onPress={() => setPaymentMethod('invoice')}
        >
          <Text>SMS Invoice</Text>
        </TouchableOpacity>
      </View>

      {paymentMethod === 'invoice' && (
        <TextInput
          style={styles.phoneInput}
          placeholder="+998901234567"
          value={phoneNumber}
          onChangeText={setPhoneNumber}
          keyboardType="phone-pad"
        />
      )}

      <TouchableOpacity 
        style={[styles.payButton, loading && styles.disabled]}
        onPress={handlePayment}
        disabled={loading || (paymentMethod === 'invoice' && !phoneNumber)}
      >
        <Text style={styles.payButtonText}>
          {loading ? 'Creating Payment...' : 'Pay with Click'}
        </Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = {
  container: {
    padding: 20,
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 20,
  },
  methodSelector: {
    flexDirection: 'row',
    marginBottom: 20,
  },
  methodButton: {
    flex: 1,
    padding: 10,
    borderWidth: 1,
    borderColor: '#ccc',
    alignItems: 'center',
  },
  selected: {
    backgroundColor: '#007bff',
  },
  phoneInput: {
    borderWidth: 1,
    borderColor: '#ccc',
    padding: 10,
    marginBottom: 20,
  },
  payButton: {
    backgroundColor: '#28a745',
    padding: 15,
    alignItems: 'center',
  },
  disabled: {
    backgroundColor: '#ccc',
  },
  payButtonText: {
    color: 'white',
    fontWeight: 'bold',
  },
};

export default ClickPaymentComponent;
```

### 6. Testing Signature Generation

Use this Node.js script to test signature generation:

```javascript
const crypto = require('crypto');

function generateClickSignature(data, secretKey) {
  const {
    click_trans_id,
    service_id,
    merchant_trans_id,
    merchant_prepare_id,
    amount,
    action,
    sign_time
  } = data;

  const formattedAmount = Number(amount).toFixed(2);
  const prepareIdPart = action == "1" ? merchant_prepare_id : "";
  
  const signStringSource = `${click_trans_id}${service_id}${secretKey}${merchant_trans_id}${prepareIdPart}${formattedAmount}${action}${sign_time}`;
  
  return crypto.createHash("md5").update(signStringSource).digest("hex");
}

// Test signature generation
const testData = {
  click_trans_id: 12345,
  service_id: 80012,
  merchant_trans_id: "test_123",
  merchant_prepare_id: "test_123",
  amount: 1000.00,
  action: 0,
  sign_time: "2025-01-01 12:00:00"
};

const secretKey = "your_secret_key";
const signature = generateClickSignature(testData, secretKey);
console.log("Generated signature:", signature);
```

### 7. Error Testing

Test various error scenarios:

```bash
# Test invalid signature
curl -X POST http://localhost:8787/api/payment/click/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "click_trans_id": 12345,
    "service_id": 80012,
    "merchant_trans_id": "test",
    "amount": 1000.00,
    "action": 0,
    "error": 0,
    "sign_time": "2025-01-01 12:00:00",
    "sign_string": "invalid_signature"
  }'

# Expected: {"error": -1, "error_note": "SIGN CHECK FAILED!"}

# Test transaction not found
curl -X POST http://localhost:8787/api/payment/click/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "click_trans_id": 12345,
    "service_id": 80012,
    "merchant_trans_id": "nonexistent_transaction",
    "amount": 1000.00,
    "action": 0,
    "error": 0,
    "sign_time": "2025-01-01 12:00:00",
    "sign_string": "valid_signature_here"
  }'

# Expected: {"error": -5, "error_note": "User does not exist"}
```
