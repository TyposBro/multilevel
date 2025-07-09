import { sign } from "hono/jwt";

export const generateToken = async (c, userId, isAdmin = false) => {
  const payload = {
    id: userId,
    exp: Math.floor(Date.now() / 1000) + 24 * 60 * 60, // 1 day expiry
  };

  const secretKeyName = isAdmin ? "JWT_SECRET_ADMIN" : "JWT_SECRET";
  const secret = c.env[secretKeyName];

  if (!secret) {
    console.error(`FATAL: JWT secret "${secretKeyName}" not found in environment.`);
    throw new Error(`Server configuration error: Missing JWT secret.`);
  }

  // THIS IS THE CRITICAL FIX
  // Explicitly set the algorithm to HS256
  const token = await sign(payload, secret, "HS256");

  return token;
};
