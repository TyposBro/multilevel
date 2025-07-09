// {PATH_TO_PROJECT}/src/controllers/adminAuthController.js
import { db } from "../db/d1-client";
import { generateToken } from "../utils/generateToken";
import { verifyPassword } from "../utils/password"; // <-- Import our new utility

/**
 * @desc    Authenticate admin & get token (Login)
 * @route   POST /api/admin/auth/login
 * @access  Public
 */
export const loginAdmin = async (c) => {
  try {
    const { email, password } = await c.req.json();

    if (!email || !password) {
      return c.json({ message: "Please provide email and password" }, 400);
    }

    const admin = await db.findAdminByEmail(c.env.DB, email);

    // Use our new verification function
    if (admin && (await verifyPassword(password, admin.password))) {
      const token = await generateToken(c, admin.id, true); // isAdmin = true

      return c.json({
        id: admin.id,
        email: admin.email,
        role: admin.role,
        token: token,
      });
    } else {
      return c.json({ message: "Invalid admin credentials" }, 401);
    }
  } catch (error) {
    console.error("Admin Login Error:", error);
    return c.json({ message: "Server error during admin login" }, 500);
  }
};
