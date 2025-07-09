// {PATH_TO_PROJECT}/src/controllers/adminAuthController.js

import { db } from "../db/d1-client";
import { generateToken } from "../utils/generateToken"; // This needs to be updated too
import { verifyPassword } from "../utils/password";

export const loginAdmin = async (c) => {
  try {
    const { email, password } = await c.req.json();

    if (!email || !password) {
      return c.json({ message: "Please provide email and password" }, 400);
    }

    const admin = await db.findAdminByEmail(c.env.DB, email);

    if (admin && (await verifyPassword(password, admin.password))) {
      // --- THIS IS THE FIX ---
      // Instead of just passing the ID, pass the whole admin object or a specific payload
      const token = await generateToken(c, { id: admin.id, email: admin.email }, true); // Pass a payload object

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
