// {PATH_TO_PROJECT}/src/middleware/adminMiddleware.js

import { jwt } from "hono/jwt";
import { db } from "../db/d1-client";

export const protectAdmin = (c, next) => {
  const jwtMiddleware = jwt({
    secret: c.env.JWT_SECRET_ADMIN,
  });

  return jwtMiddleware(c, async () => {
    try {
      const payload = c.get("jwt_payload");
      console.log("Admin JWT Payload received:", JSON.stringify(payload));

      if (!payload || !payload.id) {
        return c.json({ message: "Not authorized, admin token payload invalid" }, 401);
      }

      // Assuming findAdminByEmail or a new findAdminById exists
      const admin = await db.findAdminByEmail(c.env.DB, payload.email);

      if (!admin) {
        return c.json({ message: "Not authorized, admin not found" }, 401);
      }

      c.set("admin", admin);
      await next();
    } catch (error) {
      console.error("Error in protectAdmin middleware:", error);
      return c.json({ message: "Admin authorization error" }, 401);
    }
  });
};
