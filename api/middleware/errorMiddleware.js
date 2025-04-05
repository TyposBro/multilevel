// middleware/errorMiddleware.js

// Handle 404 Not Found errors
const notFound = (req, res, next) => {
    const error = new Error(`Not Found - ${req.originalUrl}`);
    res.status(404);
    next(error); // Pass error to the next error handler
  };
  
  // General error handler
  const errorHandler = (err, req, res, next) => {
    // Sometimes an error might come through with a 200 status code
    // If it does, set it to 500 Internal Server Error
    const statusCode = res.statusCode === 200 ? 500 : res.statusCode;
    res.status(statusCode);
  
    console.error("ERROR STACK:", err.stack); // Log stack trace for debugging
  
    res.json({
      message: err.message,
      // Optionally include stack trace in development environment only
      stack: process.env.NODE_ENV === 'production' ? null : err.stack,
    });
  };
  
  module.exports = { notFound, errorHandler };