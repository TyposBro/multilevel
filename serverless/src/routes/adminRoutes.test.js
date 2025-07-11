// serverless/src/routes/adminRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";
import { uploadToCDN } from "../services/storageService"; // We'll mock this

// 1. Mock both external dependencies
vi.mock("../db/d1-client.js");
vi.mock("../services/storageService.js");

describe("Admin Routes", () => {
  const MOCK_ADMIN_ENV = {
    JWT_SECRET_ADMIN: "a-secure-admin-secret",
    R2_PUBLIC_URL: "https://fake-r2-url.com",
    DB: db, // Pass the mocked db object
  };

  const mockAdmin = { id: "admin-1", email: "admin@test.com" };

  beforeEach(() => {
    vi.clearAllMocks();
    // Always mock the findAdminByEmail for protected routes
    db.findAdminByEmail.mockResolvedValue(mockAdmin);
  });

  it("POST /api/admin/content/part1.1 should be protected", async () => {
    const res = await app.request("/api/admin/content/part1.1", { method: "POST" }, MOCK_ADMIN_ENV);
    // Check it fails without a token
    expect(res.status).toBe(401);
  });

  // This is the new, more detailed test
  it("POST /api/admin/content/part1.1 should upload content and save to DB", async () => {
    // 2. Setup Mocks
    const fakeAudioUrl = "https://fake-r2-url.com/audio/12345-test-audio.mp3";
    const mockQuestionText = "What is your favorite color?";

    // Mock the return value of our external services
    uploadToCDN.mockResolvedValue(fakeAudioUrl);
    db.createContent.mockResolvedValue({
      id: "content-xyz-123",
      questionText: mockQuestionText,
      audioUrl: fakeAudioUrl,
    });

    const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);

    // 3. Create FormData for the request body
    const formData = new FormData();
    const audioFile = new File(["fake audio content"], "test-audio.mp3", { type: "audio/mpeg" });
    formData.append("questionText", mockQuestionText);
    formData.append("audio", audioFile);

    // 4. Make the Request
    const res = await app.request(
      "/api/admin/content/part1.1",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      },
      MOCK_ADMIN_ENV
    );

    // 5. Assert the Outcome
    expect(res.status).toBe(201);
    const body = await res.json();
    expect(body.message).toContain("uploaded successfully");
    expect(body.data.id).toBe("content-xyz-123");

    // 6. Assert the mocks were called correctly

    // Check that uploadToCDN was called with the right context, provider, file, and path
    expect(uploadToCDN).toHaveBeenCalledOnce();
    // The context object 'c' is complex, so we check for an object with the expected env
    expect(uploadToCDN).toHaveBeenCalledWith(
      expect.objectContaining({ env: MOCK_ADMIN_ENV }),
      "cloudflare", // The default provider
      audioFile,
      "audio" // The destination path for this part
    );

    // Check that createContent was called with the correct table name and data
    expect(db.createContent).toHaveBeenCalledOnce();
    expect(db.createContent).toHaveBeenCalledWith(MOCK_ADMIN_ENV.DB, "content_part1_1", {
      questionText: mockQuestionText,
      audioUrl: fakeAudioUrl,
      tags: expect.any(String), // or JSON.stringify(['admin-upload'])
    });
  });

  it("POST /api/admin/content/part3 should upload a new topic", async () => {
    // Arrange
    uploadToCDN.mockResolvedValue(null); // No image in this test
    db.createContent.mockResolvedValue({ id: "part3-abc", topic: "Technology" });
    const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);

    const formData = new FormData();
    formData.append("topic", "The Impact of AI");
    formData.append("forPoints", "Increases efficiency\nCreates new jobs");
    formData.append("againstPoints", "Job displacement\nEthical concerns");

    // Act
    const res = await app.request(
      "/api/admin/content/part3",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      },
      MOCK_ADMIN_ENV
    );

    // Assert
    expect(res.status).toBe(201);
    expect(db.createContent).toHaveBeenCalledWith(
      MOCK_ADMIN_ENV.DB,
      "content_part3",
      expect.objectContaining({
        topic: "The Impact of AI",
        forPoints: JSON.stringify(["Increases efficiency", "Creates new jobs"]),
        againstPoints: JSON.stringify(["Job displacement", "Ethical concerns"]),
        imageUrl: null,
      })
    );
  });

  it("POST /api/admin/content/part2 should return 400 if a required file is missing", async () => {
    // Arrange
    const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);
    const formData = new FormData();
    // Intentionally omit some fields to test validation
    formData.append("question1", "q1");
    formData.append("question2", "q2");
    formData.append("question3", "q3");
    formData.append("audio", new File(["content"], "test.mp3"));

    // Act
    const res = await app.request(
      "/api/admin/content/part2",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      },
      MOCK_ADMIN_ENV
    );

    // Assert
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.message).toBe("A single combined audio file is required.");
    expect(uploadToCDN).not.toHaveBeenCalled();
    expect(db.createContent).not.toHaveBeenCalled();
  });
});
