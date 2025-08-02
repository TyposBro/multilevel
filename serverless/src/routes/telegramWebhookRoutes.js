// {PATH_TO_PROJECT}/src/routes/telegramWebhookRoutes.js

import { Hono } from "hono";
import { createTelegramWebhookHandler } from "../controllers/telegramWebhookController";

const telegramWebhookRoutes = new Hono();

// --- Endpoint for the MOBILE APP bot ---
// This uses the original bot token and points to the redirect flow with the mobile deep link.
telegramWebhookRoutes.post(
  "/mobile/:token",
  createTelegramWebhookHandler({
    secretKeyName: "TELEGRAM_BOT_TOKEN",
    redirectPath: "/api/auth/telegram/redirect",
  })
);

// --- Endpoint for the WEB ACCOUNT bot ---
// This uses the new bot token and points directly to the web login flow.
telegramWebhookRoutes.post(
  "/web/:token",
  createTelegramWebhookHandler({
    secretKeyName: "TELEGRAM_BOT_TOKEN_WEB",
    redirectPath: "/api/auth/telegram/login-web",
  })
);

export default telegramWebhookRoutes;
