// {PATH_TO_PROJECT}/api/middleware/authMiddleware.js
const jwt = require("jsonwebtoken");
const User = require("../models/userModel"); // Adjust path as needed

const protect = async (req, res, next) => {
  let token;

  // Check for token in Authorization header (Bearer scheme)
  if (req.headers.authorization && req.headers.authorization.startsWith("Bearer")) {
    try {
      // Get token from header
      token = req.headers.authorization.split(" ")[1];

      // Verify token
      const decoded = jwt.verify(token, process.env.JWT_SECRET);

      // Get user from the token payload (excluding password)
      // Attach user to the request object for use in subsequent routes
      req.user = await User.findById(decoded.id).select("-password");

      if (!req.user) {
        // Handle case where user associated with token doesn't exist anymore
        return res.status(401).json({ message: "Not authorized, user not found" });
      }

      next(); // Proceed to the protected route
    } catch (error) {
      console.error("Token verification failed:", error);
      res.status(401).json({ message: "Not authorized, token failed" });
    }
  }

  if (!token) {
    res.status(401).json({ message: "Not authorized, no token" });
  }
};

module.exports = { protect };
