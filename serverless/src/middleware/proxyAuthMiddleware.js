// serverless/src/middleware/proxyAuthMiddleware.js

export const proxyAuth = async (c, next) => {
  const expectedSecret = c.env.CLOUDFLARE_WORKER_PROXY_SECRET;
  const receivedSecret = c.req.header("X-Proxy-Auth");

  if (!expectedSecret) {
    console.error("FATAL: CLOUDFLARE_WORKER_PROXY_SECRET is not set.");
    return c.json({ error: "Server configuration error" }, 500);
  }

  if (receivedSecret !== expectedSecret) {
    console.warn("Proxy authentication failed. Invalid secret received.");
    return c.json({ error: "Unauthorized" }, 401);
  }

  // If the secret is valid, proceed to the controller
  await next();
};
