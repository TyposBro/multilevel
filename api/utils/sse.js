// {PATH_TO_PROJECT}/api/utils/sse.js

function sendSseChunk(res, eventName, data) {
  if (res.writableEnded) {
    return;
  }
  try {
    res.write(`event: ${eventName}\n`);
    res.write(`data: ${JSON.stringify(data)}\n\n`);
  } catch (e) {
    console.error(`[SSE Helper] Error writing: ${e.message}`);
    if (!res.writableEnded) {
      res.end();
    }
  }
}

const sentenceTerminators = /[.!?\n]/;

// Use module.exports to be consistent with the rest of the backend
module.exports = {
  sendSseChunk,
  sentenceTerminators,
};
