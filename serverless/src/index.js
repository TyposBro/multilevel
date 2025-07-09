// {PATH_TO_PROJECT}/serverless/src/index.js
import { Hono } from 'hono';
import { logger } from 'hono/logger';

// Import routes
import authRoutes from './routes/authRoutes';
import ieltsExamRoutes from './routes/ieltsExamRoutes';
import multilevelExamRoutes from './routes/multilevelExamRoutes';
import subscriptionRoutes from './routes/subscriptionRoutes';
import wordBankRoutes from './routes/wordBankRoutes';
import adminRoutes from './routes/adminRoutes';
import telegramWebhookRoutes from './routes/telegramWebhookRoutes';
import paymentRoutes from './routes/paymentRoutes';

const app = new Hono();

// --- Middleware ---
app.use('*', logger()); // Basic request logger

// --- API Routes ---
app.route('/api/auth', authRoutes);
app.route('/api/exam/ielts', ieltsExamRoutes);
app.route('/api/exam/multilevel', multilevelExamRoutes);
app.route('/api/subscriptions', subscriptionRoutes);
app.route('/api/wordbank', wordBankRoutes);
app.route('/api/admin', adminRoutes);
app.route('/api/telegram/webhook', telegramWebhookRoutes);
app.route('/api/payment', paymentRoutes);

// --- Root and Error Handling ---
app.get('/', (c) => c.text('API is running...'));

app.notFound((c) => {
  return c.json({ message: `Not Found - ${c.req.method} ${c.req.url}` }, 404);
});

app.onError((err, c) => {
  console.error('SERVER ERROR:', err);
  return c.json(
    {
      message: err.message || 'Internal Server Error',
    },
    500
  );
});

export default app;