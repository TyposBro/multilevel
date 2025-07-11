// {PATH_TO_PROJECT}/serverless/src/index.js
import { Hono } from "hono";
import { logger } from "hono/logger";
import { cors } from "hono/cors";

// Import routes
import authRoutes from "./routes/authRoutes";
import ieltsExamRoutes from "./routes/ieltsExamRoutes";
import multilevelExamRoutes from "./routes/multilevelExamRoutes";
import subscriptionRoutes from "./routes/subscriptionRoutes";
import wordBankRoutes from "./routes/wordBankRoutes";
import adminRoutes from "./routes/adminRoutes";
import telegramWebhookRoutes from "./routes/telegramWebhookRoutes";
import paymentRoutes from "./routes/paymentRoutes";

const app = new Hono();

// --- Middleware ---
app.use(
  "*",
  logger(),
  cors({
    // THIS IS THE SECTION TO EDIT
    origin: [
      "http://localhost:3000", // Your main app's local dev server
      "https://your-production-frontend-app.com", // Your main app's live URL

      // --- ADD THESE LINES ---
      "http://localhost:5173", // Your admin panel's local dev server
      "https://typosbro.github.io", // The live URL for your admin panel
      // --- END OF ADDED LINES ---
    ],
    // The rest of the configuration is likely fine
    allowHeaders: ["Authorization", "Content-Type"],
    allowMethods: ["POST", "GET", "PUT", "DELETE", "OPTIONS"],
    exposeHeaders: ["Content-Length"],
    maxAge: 600,
    credentials: true,
  })
);
// --- API Routes ---
app.route("/api/auth", authRoutes);
app.route("/api/exam/ielts", ieltsExamRoutes);
app.route("/api/exam/multilevel", multilevelExamRoutes);
app.route("/api/subscriptions", subscriptionRoutes);
app.route("/api/wordbank", wordBankRoutes);
app.route("/api/admin", adminRoutes);
app.route("/api/telegram/webhook", telegramWebhookRoutes);
app.route("/api/payment", paymentRoutes);

// --- Root and Error Handling ---
app.get("/", (c) => c.text("API is running..."));

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
