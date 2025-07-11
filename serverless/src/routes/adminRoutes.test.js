// serverless/src/routes/adminRoutes.test.js
import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";
import { uploadToCDN } from "../services/storageService";

// Mock both external dependencies
vi.mock("../db/d1-client.js");
vi.mock("../services/storageService.js");

describe("Admin Routes", () => {
  const MOCK_ADMIN_ENV = {
    JWT_SECRET_ADMIN: "a-secure-admin-secret",
    R2_PUBLIC_URL: "https://fake-r2-url.com",
    DB: db,
  };

  const mockAdmin = { id: "admin-1", email: "admin@test.com" };

  beforeEach(() => {
    vi.clearAllMocks();
    db.findAdminByEmail.mockResolvedValue(mockAdmin);
  });

  it("POST /api/admin/content/part1.1 should be protected", async () => {
    const res = await app.request("/api/admin/content/part1.1", { method: "POST" }, MOCK_ADMIN_ENV);
    expect(res.status).toBe(401);
  });

  it("POST /api/admin/content/part1.1 should upload content and save to DB", async () => {
    const fakeAudioUrl = "https://fake-r2-url.com/audio/12345-test-audio.mp3";
    const mockQuestionText = "What is your favorite color?";

    uploadToCDN.mockResolvedValue(fakeAudioUrl);
    db.createContent.mockResolvedValue({
      id: "content-xyz-123",
      questionText: mockQuestionText,
      audioUrl: fakeAudioUrl,
    });

    const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);

    const formData = new FormData();
    const audioFile = new File(["fake audio content"], "test-audio.mp3", { type: "audio/mpeg" });
    formData.append("questionText", mockQuestionText);
    formData.append("audio", audioFile);

    const res = await app.request(
      "/api/admin/content/part1.1",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      },
      MOCK_ADMIN_ENV
    );

    expect(res.status).toBe(201);
    const body = await res.json();
    expect(body.message).toContain("uploaded successfully");

    expect(uploadToCDN).toHaveBeenCalledWith(expect.any(Object), "cloudflare", audioFile, "audio");

    expect(db.createContent).toHaveBeenCalledWith(MOCK_ADMIN_ENV.DB, "content_part1_1", {
      questionText: mockQuestionText,
      audioUrl: fakeAudioUrl,
      tags: expect.any(String),
    });
  });

  // --- START OF CORRECTED TEST ---
  it("POST /api/admin/content/part2 should return 400 if a required audio file is missing", async () => {
    // Arrange
    const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);
    const formData = new FormData();
    // Add all required text fields and image files
    formData.append("question1", "q1");
    formData.append("question2", "q2");
    formData.append("question3", "q3");
    formData.append("imageDescription", "desc");
    formData.append("image1", new File([""], "img1.jpg"));
    formData.append("image2", new File([""], "img2.jpg"));
    // But intentionally omit one of the audio files
    formData.append("audio1", new File([""], "aud1.mp3"));
    formData.append("audio2", new File([""], "aud2.mp3"));
    // OMITTING formData.append('audio3', ...);

    // Act
    const res = await app.request(
      "/api/admin/content/part1.2", // Corrected to test part 1.2 which has more files
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
    expect(body.message).toBe("All 5 files (2 images, 3 audio) are required.");
    expect(uploadToCDN).not.toHaveBeenCalled();
    expect(db.createContent).not.toHaveBeenCalled();
  });
  // --- END OF CORRECTED TEST ---

  it("POST /api/admin/content/part3 should upload a new topic", async () => {
    uploadToCDN.mockResolvedValue(null);
    db.createContent.mockResolvedValue({ id: "part3-abc", topic: "Technology" });
    const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);

    const formData = new FormData();
    formData.append("topic", "The Impact of AI");
    formData.append("forPoints", "Increases efficiency\nCreates new jobs");
    formData.append("againstPoints", "Job displacement\nEthical concerns");

    const res = await app.request(
      "/api/admin/content/part3",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      },
      MOCK_ADMIN_ENV
    );

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
});
