// {PATH_TO_PROJECT}/src/middleware/adminMiddleware.js

import { jwt } from "hono/jwt";
import { db } from "../db/d1-client"; // Using the new DB client stub

/**
 * Hono middleware to protect admin-only routes.
 * Uses a separate JWT secret (JWT_SECRET_ADMIN).
 */
export const protectAdmin = (c, next) => {
  const jwtMiddleware = jwt({
    secret: c.env.JWT_SECRET_ADMIN, // Use the specific admin secret
  });

  return jwtMiddleware(c, async () => {
    try {
      const payload = c.get("jwt_payload");
      if (!payload || !payload.id) {
        return c.json({ message: "Not authorized, admin token payload is invalid" }, 401);
      }

      // --- DB STUB ---
      // Replace with a call to fetch an admin from your database
      // const admin = await db.getAdminById(c.env.DB, payload.id);
      const admin = { _id: payload.id, role: "admin" }; // Using a stub for now
      // ---

      if (!admin) {
        return c.json({ message: "Not authorized, admin not found" }, 401);
      }

      // Set the admin object in the context
      c.set("admin", admin);
      await next();
    } catch (error) {
      console.error("Admin auth middleware error:", error);
      return c.json({ message: "Not authorized, admin token failed" }, 401);
    }
  });
};
