// {PATH_TO_PROJECT}/serverless/src/index.js
import { Hono } from "hono";
import { logger } from "hono/logger";
import { cors } from "hono/cors";

// Import routes
import authRoutes from "./routes/authRoutes";
import examRoutes from "./routes/examRoutes";
import subscriptionRoutes from "./routes/subscriptionRoutes";
import wordBankRoutes from "./routes/wordBankRoutes";
import adminRoutes from "./routes/adminRoutes";
import telegramWebhookRoutes from "./routes/telegramWebhookRoutes";
import paymentRoutes from "./routes/paymentRoutes";
import webhooks from "./routes/webhooks.js";

const app = new Hono();

// --- Middleware ---
app.use(
  "*",
  logger(),
  cors({
    origin: [
      "http://localhost:3000", // Your main app's local dev server
      "http://localhost:5500", // For local testing of static site
      "http://127.0.0.1:5500", // For local testing of static site
      "http://localhost:5173", // Your admin panel's local dev server
      "https://typosbro.github.io", // The live URL for your admin panel
      "https://milliytechnology.github.io", // The live URL for your main site
      "https://milliytechnology.org", // The live URL for your main site
      // Click domains for webhook calls
      "https://my.click.uz",
      "https://click.uz",
      "https://api.click.uz",
      "*", // Allow all origins for webhook testing (temporary)
    ],
    allowHeaders: ["Authorization", "Content-Type", "X-Requested-With"],
    allowMethods: ["POST", "GET", "PUT", "DELETE", "OPTIONS"],
    exposeHeaders: ["Content-Length"],
    maxAge: 600,
    credentials: true,
  })
);
// --- API Routes ---
app.route("/api/auth", authRoutes);
app.route("/api/exam/multilevel", examRoutes);
app.route("/api/subscriptions", subscriptionRoutes);
app.route("/api/wordbank", wordBankRoutes);
app.route("/api/admin", adminRoutes);
app.route("/api/telegram/webhook", telegramWebhookRoutes);
app.route("/api/payment", paymentRoutes);
app.route("/webhooks", webhooks);

// --- Root and Error Handling ---
app.get("/live", (c) => c.text("API is running..."));

app.notFound((c) => {
  return c.json({ message: `Not Found - ${c.req.method} ${c.req.url}` }, 404);
});

app.onError((err, c) => {
  console.error("SERVER ERROR:", err);
  return c.json(
    {
      message: err.message || "Internal Server Error",
    },
    500
  );
});

export default app;
