// serverless/src/services/storageService.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import { uploadToCDN } from "./storageService";

describe("Storage Service", () => {
  let mockR2Bucket;
  let mockContext;

  beforeEach(() => {
    mockR2Bucket = {
      put: vi.fn().mockResolvedValue({}),
    };
    mockContext = {
      env: {
        CDN_BUCKET: mockR2Bucket,
        R2_PUBLIC_URL: "https://my-public-url.com",
      },
    };
  });

  it("uploadToCDN should call R2 put with correct parameters", async () => {
    const file = new File(["content"], "test with spaces.txt", { type: "text/plain" });
    const destinationPath = "uploads";

    const url = await uploadToCDN(mockContext, "cloudflare", file, destinationPath);

    // Check the returned public URL
    expect(url).toContain("https://my-public-url.com/uploads/");
    expect(url).toContain("-test_with_spaces.txt"); // Note the space is replaced with an underscore

    // Check the call to the R2 bucket
    expect(mockR2Bucket.put).toHaveBeenCalledOnce();
    const [path, buffer, metadata] = mockR2Bucket.put.mock.calls[0];

    // --- THIS IS THE FIX for startsWith / toEndWith ---
    // Use standard string methods and assert their boolean result.
    expect(path.startsWith(`${destinationPath}/`)).toBe(true);
    expect(path.endsWith("-test_with_spaces.txt")).toBe(true);
    // --- END OF FIX ---

    expect(metadata.httpMetadata.contentType).toBe("text/plain");
  });

  it("uploadToCDN should throw an error for an unsupported provider", () => {
    // Can remove `async` from the test
    const file = new File(["content"], "test.txt");

    // --- THIS IS THE FIX ---
    // Wrap the function call that is expected to throw in a "thunk" (a zero-argument function).
    // Use .toThrow() without .rejects because the error is synchronous.
    const uploadFn = () => uploadToCDN(mockContext, "aws-s3", file, "uploads");

    expect(uploadFn).toThrow("Invalid or unsupported storage provider: aws-s3. Use 'cloudflare'.");
    // --- END OF FIX ---
  });

  it("uploadToCDN should throw an error if R2 bucket is not configured", async () => {
    const file = new File(["content"], "test.txt");
    const badContext = { env: { R2_PUBLIC_URL: "..." } };

    // This test is still correct because the error is thrown inside the *async* `uploadToR2` function
    await expect(uploadToCDN(badContext, "cloudflare", file, "uploads")).rejects.toThrow(
      "Cloudflare R2 bucket binding 'CDN_BUCKET' not found in wrangler.toml."
    );
  });

  // --- THIS IS THE FIX for testing thrown errors in async functions ---
  it("uploadToCDN should throw an error if R2 bucket is not configured", async () => {
    const file = new File(["content"], "test.txt");
    const badContext = { env: { R2_PUBLIC_URL: "..." } };

    // Use `await expect(...).rejects.toThrow(...)` for async functions
    await expect(uploadToCDN(badContext, "cloudflare", file, "uploads")).rejects.toThrow(
      "Cloudflare R2 bucket binding 'CDN_BUCKET' not found in wrangler.toml."
    );
  });
  // --- END OF FIX ---
});
