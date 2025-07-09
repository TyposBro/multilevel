// {PATH_TO_PROJECT}/src/middleware/adminMiddleware.js

import { verify } from "hono/jwt";
import { db } from "../db/d1-client";

export const protectAdmin = async (c, next) => {
  console.log("Entering manual ADMIN protection middleware...");

  const authHeader = c.req.header("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    console.log("Admin route: No or malformed Authorization header found.");
    return c.json({ message: "Authorization header is missing or malformed" }, 401);
  }

  const token = authHeader.substring(7);
  // CRITICAL: Use the correct admin secret!
  const secret = c.env.JWT_SECRET_ADMIN;

  if (!secret) {
    console.error("FATAL: JWT_SECRET_ADMIN not found in environment for manual verification.");
    return c.json({ message: "Server configuration error" }, 500);
  }

  try {
    console.log(`Attempting to manually verify ADMIN token...`);

    // Manually verify the token with the ADMIN secret and HS256 algorithm
    const payload = await verify(token, secret, "HS256");

    console.log("Manual ADMIN verification successful. Payload:", JSON.stringify(payload));

    // You were looking for payload.email, so ensure it's there
    if (!payload || !payload.id || !payload.email) {
      console.log("Admin payload is missing 'id' or 'email' field.");
      return c.json({ message: "Invalid admin token payload" }, 401);
    }

    // Find the admin by email, as per your original logic
    const admin = await db.findAdminByEmail(c.env.DB, payload.email);
    if (!admin) {
      console.log(`Admin with email ${payload.email} not found in database.`);
      return c.json({ message: "Admin user not found" }, 401);
    }

    console.log("Admin user successfully loaded from database.");
    c.set("admin", admin);

    await next();
  } catch (error) {
    console.error("ADMIN TOKEN VERIFICATION FAILED:", error.message);
    return c.json({ message: "Admin token is invalid or expired", error: error.message }, 401);
  }
};
