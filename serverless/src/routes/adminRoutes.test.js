import { describe, it, expect, vi, beforeEach } from "vitest";
import app from "../index";
import { generateToken } from "../utils/generateToken";
import { db } from "../db/d1-client";
import { uploadToCDN } from "../services/storageService";

vi.mock("../db/d1-client.js");
vi.mock("../services/storageService.js");

describe("Admin Routes", () => {
  const MOCK_ADMIN_ENV = {
    JWT_SECRET_ADMIN: "a-secure-admin-secret",
    R2_PUBLIC_URL: "https://fake-r2-url.com",
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("POST /api/admin/content/part1.1 should be protected", async () => {
    const res = await app.request("/api/admin/content/part1.1", { method: "POST" }, MOCK_ADMIN_ENV);
    expect(res.status).toBe(401);
  });

  it("POST /api/admin/content/part1.1 should succeed for a valid admin", async () => {
    const mockAdmin = { id: "admin-1", email: "admin@test.com" };
    const token = await generateToken({ env: MOCK_ADMIN_ENV }, mockAdmin, true);

    db.findAdminByEmail.mockResolvedValue(mockAdmin);
    uploadToCDN.mockResolvedValue("http://fake.cdn/audio.mp3");
    db.createContent.mockResolvedValue({ id: "content-1", questionText: "Test?" });

    const formData = new FormData();
    formData.append("questionText", "What is your name?");
    formData.append("audio", new File(["content"], "test.mp3", { type: "audio/mpeg" }));

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
    expect(uploadToCDN).toHaveBeenCalled();
  });
});
