// {PATH_TO_PROJECT}/src/routes/telegramWebhookRoutes.js

import { Hono } from "hono";
import { handleWebhook } from "../controllers/telegramWebhookController";

const telegramWebhookRoutes = new Hono();

// A single generic endpoint that passes the token from the URL to the handler.
// The check for the correct bot token happens inside the controller.
telegramWebhookRoutes.post("/:token", handleWebhook);

export default telegramWebhookRoutes;
