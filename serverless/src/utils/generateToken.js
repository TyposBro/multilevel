// serverless/src/utils/generateToken.js
import { sign } from "hono/jwt";

/**
 * Generates a JWT. This version correctly handles both string and object inputs.
 * @param {object} c - The Hono context.
 * @param {string | object} payloadInput - The data for the payload. Can be a string (userId) or an object ({ id, email, ... }).
 * @param {boolean} [isAdmin=false] - Whether to use the admin secret.
 * @returns {Promise<string>} The generated JWT.
 */
export const generateToken = async (c, payloadInput, isAdmin = false) => {
  let basePayload;

  if (typeof payloadInput === "string") {
    // This handles the user ID string case correctly
    basePayload = { id: payloadInput };
  } else if (typeof payloadInput === "object" && payloadInput !== null) {
    // --- THIS IS THE FIX ---
    // If we receive an object, we use it directly as the base payload.
    basePayload = payloadInput;
    // --- END OF FIX ---
  } else {
    throw new Error("Invalid payload data provided to generateToken");
  }

  // Combine the base payload with the expiration time.
  const finalPayload = {
    ...basePayload,
    exp: Math.floor(Date.now() / 1000) + 24 * 60 * 60, // 1 day expiry
  };

  const secretKeyName = isAdmin ? "JWT_SECRET_ADMIN" : "JWT_SECRET";
  const secret = c.env[secretKeyName];

  if (!secret) {
    console.error(`FATAL: JWT secret "${secretKeyName}" not found in environment.`);
    throw new Error(`Server configuration error: Missing JWT secret.`);
  }

  const token = await sign(finalPayload, secret, "HS256");

  return token;
};
