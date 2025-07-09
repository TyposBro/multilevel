// {PATH_TO_PROJECT}/src/utils/generateToken.js

import { sign } from "hono/jwt";

/**
 * Generates a JWT for a given user ID using Hono's built-in JWT utility.
 * @param {object} c - The Hono context, to access secrets and env vars.
 * @param {string} userId - The ID to include in the token payload.
 * @param {boolean} [isAdmin=false] - Whether to generate an admin token with a different secret.
 * @returns {Promise<string>} The generated JWT.
 */
export const generateToken = async (c, userId, isAdmin = false) => {
  const payload = {
    id: userId,
    // Set expiration time (iat is "issued at", exp is "expiration time")
    // Note: Hono's `sign` uses numeric epoch seconds for exp.
    exp: Math.floor(Date.now() / 1000) + (isAdmin ? 24 * 60 * 60 : 24 * 60 * 60), // Example: 1 day expiry
  };

  const secret = isAdmin ? c.env.JWT_SECRET_ADMIN : c.env.JWT_SECRET;

  if (!secret) {
    const secretName = isAdmin ? "JWT_SECRET_ADMIN" : "JWT_SECRET";
    throw new Error(`FATAL: ${secretName} is not set in wrangler.toml secrets.`);
  }

  const token = await sign(payload, secret);
  return token;
};
