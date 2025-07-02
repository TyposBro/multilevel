const express = require("express");
const dotenv = require("dotenv");
const cors = require("cors");
const connectDB = require("./config/db");
const { notFound, errorHandler } = require("./middleware/errorMiddleware");

dotenv.config();
connectDB();

const app = express();

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: false }));

app.get("/", (req, res) => {
  res.send("API is running...");
});

// --- API Routes ---
app.use("/api/auth", require("./routes/authRoutes"));

// --- Mount both exam routes under separate namespaces ---
app.use("/api/exam/ielts", require("./routes/ieltsExamRoutes"));
app.use("/api/exam/multilevel", require("./routes/multilevelExamRoutes"));
app.use("/api/subscriptions", require("./routes/subscriptionRoutes"));
app.use("/api/wordbank", require("./routes/wordBankRoutes"));
app.use("/api/admin", require("./routes/adminRoutes"));
app.use("/api/telegram/webhook", require("./routes/telegramWebhookRoutes"));
app.use("/api/payment", require("./routes/paymentRoutes"));
// ---

app.use(notFound);
app.use(errorHandler);

const PORT = process.env.PORT || 5000;
app.listen(PORT, () => console.log(`Server running on port ${PORT}`));
