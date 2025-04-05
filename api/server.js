// server.js
const express = require('express');
const dotenv = require('dotenv');
const cors = require('cors');
const connectDB = require('./config/db');
const authRoutes = require('./routes/authRoutes');
const chatRoutes = require('./routes/chatRoutes');
const { notFound, errorHandler } = require('./middleware/errorMiddleware');

// Load environment variables
dotenv.config();

// Connect to Database
connectDB();

const app = express();

// --- Middleware ---
app.use(cors()); // Enable Cross-Origin Resource Sharing
app.use(express.json()); // Parse JSON request bodies
app.use(express.urlencoded({ extended: false })); // Parse URL-encoded bodies

// --- API Routes ---
app.get('/', (req, res) => { // Basic test route
  res.send('API is running...');
});

app.use('/api/auth', authRoutes);
app.use('/api/chat', chatRoutes);

// --- Error Handling Middleware ---
// Add AFTER your routes
// We need to create this middleware file
app.use(notFound);
app.use(errorHandler);

// --- Start Server ---
const PORT = process.env.PORT || 5000; // Fallback port

app.listen(PORT, () => console.log(`Server running on port ${PORT}`));