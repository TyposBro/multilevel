#!/bin/bash

# Google Service Account Setup Script
# Run this to create the service account for Google Play API access

echo "🔧 Setting up Google Service Account for Google Play API..."

# Set your project ID
PROJECT_ID="multilevel-5454"  # Replace with your actual project ID
SERVICE_ACCOUNT_NAME="google-play-api-service"
KEY_FILE="google-play-service-account.json"

echo "📋 Project ID: $PROJECT_ID"
echo "🔑 Service Account: $SERVICE_ACCOUNT_NAME"

# Create service account
echo "Creating service account..."
gcloud iam service-accounts create $SERVICE_ACCOUNT_NAME \
    --display-name="Google Play API Service Account" \
    --description="Service account for accessing Google Play Developer API" \
    --project=$PROJECT_ID

# Grant necessary permissions
echo "Granting permissions..."
gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/androidpublisher.purchases"

# Create and download key
echo "Creating service account key..."
gcloud iam service-accounts keys create $KEY_FILE \
    --iam-account="$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --project=$PROJECT_ID

echo "✅ Service account created successfully!"
echo "📁 Key file saved as: $KEY_FILE"
echo ""
echo "🔒 IMPORTANT: Add this key to your Cloudflare Workers environment:"
echo "Variable name: GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"
echo "Value: Contents of $KEY_FILE (as a single line JSON string)"
echo ""
echo "🔗 Next steps:"
echo "1. Upload the key to your Cloudflare Workers"
echo "2. Set up subscription products in Google Play Console"
echo "3. Test the integration"

# Display the service account email for Google Play Console
echo ""
echo "📧 Service Account Email (for Google Play Console):"
echo "$SERVICE_ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com"
