// {PATH_TO_PROJECT}/src/middleware/authMiddleware.js

import { jwt } from "hono/jwt";
import { db } from "../db/d1-client"; // Using the new DB client stub

/**
 * Hono middleware to protect user routes.
 * 1. Verifies the JWT token.
 * 2. Fetches the user from the database.
 * 3. Attaches the user object to the context (`c.set('user', ...)`)
 */
export const protect = (c, next) => {
  // `hono/jwt` handles extracting the 'Bearer' token and verifying it.
  const jwtMiddleware = jwt({
    secret: c.env.JWT_SECRET,
  });

  // The second argument to `jwtMiddleware` is a function that runs *after* successful verification.
  return jwtMiddleware(c, async () => {
    try {
      const payload = c.get("jwt_payload");
      if (!payload || !payload.id) {
        return c.json({ message: "Not authorized, token payload is invalid" }, 401);
      }

      // Replace Mongoose call with our new DB client function
      const user = await db.getUserById(c.env.DB, payload.id);

      if (!user) {
        return c.json({ message: "Not authorized, user not found" }, 401);
      }

      // Set the user object in the context for the next middleware or route handler
      c.set("user", user);
      await next();
    } catch (error) {
      console.error("Auth middleware error:", error);
      return c.json({ message: "Not authorized, token failed" }, 401);
    }
  });
};
