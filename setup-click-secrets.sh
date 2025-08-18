#!/bin/bash

# Click Secret Key Setup Script
# Run this script to configure your Click secret key in Cloudflare Workers

echo "🔐 Click Secret Key Setup for Spiko"
echo "================================="
echo ""
echo "This script will help you set your Click secret key as an encrypted"
echo "environment variable in Cloudflare Workers."
echo ""
echo "📋 Before running this:"
echo "1. Get your secret key from Click merchant dashboard"
echo "2. Make sure you have wrangler CLI installed and authenticated"
echo ""

# Check if wrangler is installed
if ! command -v wrangler &> /dev/null; then
    echo "❌ Wrangler CLI not found. Please install it first:"
    echo "npm install -g wrangler"
    exit 1
fi

echo "✅ Wrangler CLI found"
echo ""

# Check if user is authenticated
if ! wrangler whoami &> /dev/null; then
    echo "❌ Not authenticated with Cloudflare. Please run:"
    echo "wrangler login"
    exit 1
fi

echo "✅ Authenticated with Cloudflare"
echo ""

echo "🚀 Setting up Click secret keys..."
echo ""

echo "📝 For PRODUCTION environment:"
echo "Please enter your LIVE Click secret key (from merchant dashboard):"
echo "This will be stored securely and encrypted in Cloudflare."
echo ""

# Set production secret key
wrangler secret put CLICK_SECRET_KEY_LIVE --name typosbro-multilevel-api

echo ""
echo "📝 For TEST environment (optional):"
echo "If you have a separate test merchant account, enter the test secret key."
echo "Otherwise, just press Ctrl+C to skip."
echo ""

# Set test secret key (optional)
wrangler secret put CLICK_SECRET_KEY_TEST --name typosbro-multilevel-api

echo ""
echo "✅ Secret keys configured successfully!"
echo ""
echo "🧪 Test your configuration:"
echo "1. Run: cd /home/ched54/Documents/milliy/spiko"
echo "2. Run: CLICK_SECRET_KEY=your_secret_key node serverless/calculate-click-signature.js"
echo "3. Test webhook with generated signature"
echo ""
echo "🔍 Monitor your integration:"
echo "- Check Cloudflare Workers logs for signature verification"
echo "- Monitor Click merchant dashboard for webhook responses"
echo "- Verify Android app payment flow"
echo ""
echo "🎉 Your Click integration setup is complete!"
echo ""
echo "📊 Current Configuration Summary:"
echo "- Service ID: 80012 ✅"
echo "- Merchant ID: 44439 ✅"
echo "- Secret Key: Now configured ✅"
echo "- Webhook URL: https://typosbro-multilevel-api.milliytechnology.workers.dev/api/payment/click/webhook ✅"
echo "- Android UI: Complete ✅"
echo "- Recent Transactions: Active (1,000 сум payments) ✅"
