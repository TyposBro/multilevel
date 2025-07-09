// {PATH_TO_PROJECT}/src/utils/sse.js

/**
 * Creates and returns a Server-Sent Events (SSE) stream response for Hono.
 *
 * This function sets up a streaming response body and provides a helper
 * function to send events to the client.
 *
 * @param {object} c - The Hono context.
 * @param {Function} callback - An async function that receives the `sendEvent` helper.
 *                              You will call `sendEvent` from within this callback to
 *                              stream data to the client.
 *
 * @returns {Response} A Response object with the appropriate headers for SSE.
 *
 * @example
 * return streamSse(c, async (sendEvent) => {
 *   await sendEvent({ event: 'message', data: { text: 'Hello' } });
 *   // ... do more work ...
 *   await sendEvent({ event: 'message', data: { text: 'World' } });
 *   // The stream will be closed automatically when the callback finishes.
 * });
 */
export const streamSse = (c, callback) => {
  let controller;

  const stream = new ReadableStream({
    start(c) {
      controller = c;
    },
  });

  const sendEvent = async (message) => {
    if (!controller) return;
    const { event, data, id } = message;
    let payload = "";
    if (event) payload += `event: ${event}\n`;
    if (id) payload += `id: ${id}\n`;
    payload += `data: ${JSON.stringify(data)}\n\n`;

    try {
      controller.enqueue(new TextEncoder().encode(payload));
    } catch (e) {
      console.error("Failed to enqueue SSE event", e);
    }
  };

  // Run the user's logic, which will use sendEvent to push data.
  // When the callback finishes, close the stream.
  callback(sendEvent)
    .catch((err) => {
      console.error("Error in SSE callback:", err);
      // Optionally send an error event to the client
      sendEvent({ event: "error", data: { message: err.message } });
    })
    .finally(() => {
      if (controller) {
        controller.close();
      }
    });

  // Return the response object to Hono
  return c.stream(stream, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    },
  });
};
