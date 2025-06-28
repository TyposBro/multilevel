# .env

PORT=3000 <!-- or any port you want -->
MONGO_URI=your-mongo-db-uri
JWT_SECRET=your-jwt-secrete
JWT_EXPIRES_IN=1d # Token expiration time (e.g., 1d, 7d, 1h)

GEMINI_API_KEY=your-gemini-api
GOOGLE_APPLICATION_CREDENTIALS=your-google-cloud-project-secrete-key

Set the Webhook with Telegram:
You only need to do this once (or if your ngrok URL changes). Open a browser and visit this URL, replacing the placeholders:

```

https://api.telegram.org/bot<YOUR_BOT_TOKEN>/setWebhook?url=<YOUR_NGROK_URL>/api/telegram/webhook/<YOUR_BOT_TOKEN>

```

Example: <https://api.telegram.org/bot123/setWebhook?url=https://b4a7.ngrok-free.app/api/telegram/webhook/123>

## DONT FORGET TO SET BOT DOMAIN ON TELEGRAM BOTFATHER
