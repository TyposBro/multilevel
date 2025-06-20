// {PATH_TO_PROJECT}/api/controllers/adminAuthController.js

const Admin = require("../models/AdminModel");
const jwt = require("jsonwebtoken");

// Generate a token specifically for an admin
const generateAdminToken = (adminId) => {
  return jwt.sign({ id: adminId }, process.env.JWT_SECRET_ADMIN, {
    expiresIn: process.env.JWT_EXPIRES_IN_ADMIN,
  });
};

/**
 * @desc    Authenticate admin & get token (Login)
 * @route   POST /api/admin/auth/login
 * @access  Public
 */
const loginAdmin = async (req, res) => {
  const { email, password } = req.body;

  if (!email || !password) {
    return res.status(400).json({ message: "Please provide email and password" });
  }

  try {
    const admin = await Admin.findOne({ email }).select("+password");

    if (admin && (await admin.matchPassword(password))) {
      res.json({
        _id: admin._id,
        email: admin.email,
        role: admin.role,
        token: generateAdminToken(admin._id),
      });
    } else {
      res.status(401).json({ message: "Invalid admin credentials" });
    }
  } catch (error) {
    console.error("Admin Login Error:", error);
    res.status(500).json({ message: "Server error during admin login" });
  }
};

module.exports = { loginAdmin };
