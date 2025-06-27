const express = require("express");
const dotenv = require("dotenv");
const cors = require("cors");
const connectDB = require("./config/db");
const authRoutes = require("./routes/authRoutes");
const ieltsExamRoutes = require("./routes/ieltsExamRoutes");
const multilevelExamRoutes = require("./routes/multilevelExamRoutes");
const subscriptionRoutes = require("./routes/subscriptionRoutes");
const wordBankRoutes = require("./routes/wordBankRoutes");
const { notFound, errorHandler } = require("./middleware/errorMiddleware");
const adminRoutes = require("./routes/adminRoutes");

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
app.use("/api/auth", authRoutes);

// --- Mount both exam routes under separate namespaces ---
app.use("/api/exam/ielts", ieltsExamRoutes);
app.use("/api/exam/multilevel", multilevelExamRoutes);
app.use("/api/subscriptions", subscriptionRoutes);
app.use("/api/wordbank", wordBankRoutes);
app.use("/api/admin", adminRoutes);
// ---

app.use(notFound);
app.use(errorHandler);

const PORT = process.env.PORT || 5000;
app.listen(PORT, () => console.log(`Server running on port ${PORT}`));
