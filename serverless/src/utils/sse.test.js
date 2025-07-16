import { describe, it, expect, vi, afterEach } from "vitest";
import { streamSse } from "./sse";

describe("SSE Utility", () => {
  const originalReadableStream = global.ReadableStream;
  const originalTextEncoder = global.TextEncoder;

  afterEach(() => {
    global.ReadableStream = originalReadableStream;
    global.TextEncoder = originalTextEncoder;
  });

  it("should stream data correctly and close the stream", async () => {
    const controllerMock = {
      enqueue: vi.fn(),
      close: vi.fn(),
    };
    const cMock = { stream: vi.fn() };

    global.ReadableStream = vi.fn((streamer) => {
      streamer.start(controllerMock);
      return { type: "mock-stream" };
    });
    global.TextEncoder = vi.fn(() => ({
      encode: (str) => str,
    }));

    const sseCallback = async (sendEvent) => {
      await sendEvent({ event: "greeting", data: { msg: "hello" } });
      await sendEvent({ data: { msg: "world" } });
      await sendEvent({ id: "123", data: "just a string" });
    };

    streamSse(cMock, sseCallback);
    await new Promise((resolve) => setImmediate(resolve));

    expect(cMock.stream).toHaveBeenCalledOnce();
    expect(cMock.stream.mock.calls[0][1].headers).toEqual({
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    });

    expect(controllerMock.enqueue).toHaveBeenCalledTimes(3);
    expect(controllerMock.enqueue).toHaveBeenCalledWith(
      'event: greeting\ndata: {"msg":"hello"}\n\n'
    );
    expect(controllerMock.enqueue).toHaveBeenCalledWith('data: {"msg":"world"}\n\n');
    expect(controllerMock.enqueue).toHaveBeenCalledWith('id: 123\ndata: "just a string"\n\n');
    expect(controllerMock.close).toHaveBeenCalledOnce();
  });

  it("should handle errors in the callback and send an error event", async () => {
    const controllerMock = {
      enqueue: vi.fn(),
      close: vi.fn(),
    };
    const cMock = { stream: vi.fn() };
    global.ReadableStream = vi.fn((s) => s.start(controllerMock));
    global.TextEncoder = vi.fn(() => ({ encode: (s) => s }));

    const errorMessage = "Something went wrong";
    const sseCallbackWithError = async () => {
      throw new Error(errorMessage);
    };

    streamSse(cMock, sseCallbackWithError);
    await new Promise((resolve) => setImmediate(resolve));

    expect(controllerMock.enqueue).toHaveBeenCalledTimes(1);
    expect(controllerMock.enqueue).toHaveBeenCalledWith(
      `event: error\ndata: {"message":"${errorMessage}"}\n\n`
    );
    expect(controllerMock.close).toHaveBeenCalledOnce();
  });
});
