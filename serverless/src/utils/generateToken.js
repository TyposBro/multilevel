// {PATH_TO_PROJECT}/src/utils/generateToken.js

import { sign } from "hono/jwt";

/**
 * Generates a JWT.
 * @param {object} c - The Hono context.
 * @param {object} payloadData - The data for the payload (e.g., { id: '...', email: '...' }).
 * @param {boolean} [isAdmin=false] - Whether to use the admin secret.
 * @returns {Promise<string>} The generated JWT.
 */
export const generateToken = async (c, payloadData, isAdmin = false) => {
  // Combine the incoming payload with the expiration time
  const payload = {
    ...payloadData,
    exp: Math.floor(Date.now() / 1000) + 24 * 60 * 60, // 1 day expiry
  };

  const secretKeyName = isAdmin ? "JWT_SECRET_ADMIN" : "JWT_SECRET_ADMIN";
  const secret = c.env[secretKeyName];

  if (!secret) {
    console.error(`FATAL: JWT secret "${secretKeyName}" not found in environment.`);
    throw new Error(`Server configuration error: Missing JWT secret.`);
  }

  const token = await sign(payload, secret, "HS256");
  return token;
};
