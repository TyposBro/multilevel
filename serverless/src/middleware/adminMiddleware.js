import { jwt } from "hono/jwt";
import { db } from "../db/d1-client";

// This middleware combines JWT verification and loading the admin user.
export const protectAdmin = async (c, next) => {
  // 1. Use Hono's built-in JWT middleware to verify the token first.
  const jwtMiddleware = jwt({
    secret: c.env.JWT_SECRET_ADMIN,
    alg: "HS256",
  });

  // Create a dummy next function to capture the result of the JWT middleware
  let jwtError = null;
  await jwtMiddleware(c, async () => {}).catch((e) => {
    jwtError = e;
  });

  if (jwtError) {
    // If the JWT middleware failed, it's an auth error (401)
    return c.json({ message: jwtError.message || "Invalid or expired token" }, 401);
  }

  // 2. If the token is valid, load the admin from the database.
  try {
    const payload = c.get("jwtPayload"); // Hono's middleware puts the payload here
    if (!payload || !payload.id) {
      return c.json({ message: "Token payload is invalid" }, 401);
    }

    const admin = await db.findAdminByEmail(c.env.DB, payload.email);

    if (!admin) {
      return c.json({ message: "Admin not found" }, 401);
    }

    // 3. Attach the admin to the context and proceed.
    c.set("admin", admin);
    await next();
  } catch (error) {
    console.error("Error in protectAdmin after JWT verification:", error);
    return c.json({ message: "Server error during admin authorization" }, 500);
  }
};
