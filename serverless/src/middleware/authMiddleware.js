import { verify } from "hono/jwt";
import { db } from "../db/d1-client";

export const protectAndLoadUser = async (c, next) => {
  console.log("Entering manual protection middleware...");

  const authHeader = c.req.header("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    console.log("No or malformed Authorization header found.");
    return c.json({ message: "Authorization header is missing or malformed" }, 401);
  }

  const token = authHeader.substring(7); // Remove "Bearer "
  const secret = c.env.JWT_SECRET;

  if (!secret) {
    console.error("FATAL: JWT_SECRET not found in environment for manual verification.");
    return c.json({ message: "Server configuration error" }, 500);
  }

  try {
    console.log(`Attempting to manually verify token starting with: ${token.substring(0, 10)}...`);

    // Manually verify the token
    const payload = await verify(token, secret, "HS256");

    console.log("Manual verification successful. Payload:", JSON.stringify(payload));

    if (!payload || !payload.id) {
      console.log("Payload is missing 'id' field.");
      return c.json({ message: "Invalid token payload" }, 401);
    }

    const user = await db.getUserById(c.env.DB, payload.id);
    if (!user) {
      console.log(`User with id ${payload.id} not found in database.`);
      return c.json({ message: "User not found" }, 401);
    }

    console.log("User successfully loaded from database.");
    c.set("user", user);

    // If everything is successful, proceed to the controller
    await next();
  } catch (error) {
    // This will catch the specific error from the `verify` function
    console.error("TOKEN VERIFICATION FAILED:", error.message);
    console.error("Full error object:", JSON.stringify(error));
    return c.json({ message: "Token is invalid or expired", error: error.message }, 401);
  }
};
