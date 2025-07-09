// {PATH_TO_PROJECT}/src/routes/telegramWebhookRoutes.js

import { Hono } from "hono";
import { handleWebhook } from "../controllers/telegramWebhookController";

const telegramWebhookRoutes = new Hono();

// The Hono router itself doesn't have direct access to env variables like Express did
// with `process.env`. The context `c` passed to the handler will have them.
// So, we define a generic route and let the main `index.js` handle the full path.
// The check for the correct bot token will happen inside the controller.
telegramWebhookRoutes.post("/:token", handleWebhook);

export default telegramWebhookRoutes;
