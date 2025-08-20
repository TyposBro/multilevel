#!/bin/bash

# Google Play Billing Testing Script
# This script helps you test the Google Play Billing implementation

echo "🎯 Google Play Billing Testing Guide"
echo "====================================="
echo ""

echo "📱 1. BUILDING DEBUG APK"
echo "Building debug APK with fake billing client..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ Debug build successful!"
    echo ""
else
    echo "❌ Debug build failed! Check the errors above."
    exit 1
fi

echo "📱 2. BUILDING RELEASE APK (for real Google Play testing)"
echo "Building release APK with real billing client..."
./gradlew assembleRelease

if [ $? -eq 0 ]; then
    echo "✅ Release build successful!"
    echo ""
else
    echo "❌ Release build failed! Check the errors above."
    exit 1
fi

echo "🔧 3. TESTING RECOMMENDATIONS"
echo "=============================="
echo ""
echo "DEBUG TESTING (using FakeBillingClientWrapper):"
echo "• Install debug APK: adb install app/build/outputs/apk/debug/app-debug.apk"
echo "• Navigate to subscription screen"
echo "• Test purchase flows - they will use fake billing client"
echo "• Add navigation to BillingDebugScreen for detailed testing"
echo ""
echo "RELEASE TESTING (using real Google Play Billing):"
echo "• Upload signed APK to Google Play Console"
echo "• Create subscription products (silver_monthly, gold_monthly)"
echo "• Add test accounts in Play Console"
echo "• Install via Play Store (not direct APK)"
echo "• Test real purchases with test accounts"
echo ""
echo "🔍 DEBUGGING:"
echo "=============="
echo "• View logs: adb logcat | grep BillingClient"
echo "• Check subscription status in your backend"
echo "• Verify Google Play Console setup"
echo ""
echo "📋 CHECKLIST FOR PRODUCTION:"
echo "============================"
echo "☐ Products created in Google Play Console"
echo "☐ Test accounts added and working"
echo "☐ Backend verification service configured"
echo "☐ Google Service Account credentials set up"
echo "☐ Purchase restoration working"
echo "☐ Error handling tested"
echo "☐ Analytics tracking implemented"
echo ""
echo "🎉 Google Play Billing implementation is ready!"
echo "Check the documentation at android/docs/GOOGLE_PLAY_BILLING_GUIDE.md"
