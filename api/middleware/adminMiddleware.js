// {PATH_TO_PROJECT}/api/middleware/adminMiddleware.js

const jwt = require("jsonwebtoken");
const Admin = require("../models/AdminModel"); // Import the new Admin model

// Middleware to protect admin routes
const protectAdmin = async (req, res, next) => {
  let token;

  if (req.headers.authorization && req.headers.authorization.startsWith("Bearer")) {
    try {
      token = req.headers.authorization.split(" ")[1];

      // Verify token
      const decoded = jwt.verify(token, process.env.JWT_SECRET_ADMIN); // Use a DIFFERENT secret for admins

      // Get admin user from the token and attach to request
      req.admin = await Admin.findById(decoded.id).select("-password");

      if (!req.admin) {
        return res.status(401).json({ message: "Not authorized, admin not found" });
      }

      next();
    } catch (error) {
      console.error("Admin token verification failed:", error);
      res.status(401).json({ message: "Not authorized, token failed" });
    }
  }

  if (!token) {
    res.status(401).json({ message: "Not authorized, no token" });
  }
};

module.exports = { protectAdmin };
