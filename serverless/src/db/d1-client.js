// {PATH_TO_PROJECT}/src/db/d1-client.js

export const db = {
  // --- User Functions ---

  /**
   * Finds a user by their internal ID.
   * @param {D1Database} d1 - The D1 binding.
   * @param {string} id - The user's ID.
   * @returns {Promise<object|null>} The user object or null if not found.
   */
  async getUserById(d1, id) {
    try {
      return await d1.prepare("SELECT * FROM users WHERE id = ?").bind(id).first();
    } catch (e) {
      console.error("D1 getUserById Error:", e.message);
      return null;
    }
  },

  /**
   * Finds a user by their authentication provider ID (e.g., Google SUB or Telegram ID).
   * @param {D1Database} d1 - The D1 binding.
   * @param {{ provider: 'google'|'telegram', id: string|number }} providerInfo
   * @returns {Promise<object|null>}
   */
  async findUserByProviderId(d1, { provider, id }) {
    let query;
    if (provider === "google") {
      query = "SELECT * FROM users WHERE googleId = ?";
    } else if (provider === "telegram") {
      query = "SELECT * FROM users WHERE telegramId = ?";
    } else {
      return null; // Or throw an error for unsupported providers
    }
    try {
      return await d1.prepare(query).bind(id).first();
    } catch (e) {
      console.error("D1 findUserByProviderId Error:", e.message);
      return null;
    }
  },

  /**
   * Creates a new user in the database.
   * @param {D1Database} d1 - The D1 binding.
   * @param {object} userData - Data for the new user.
   * @returns {Promise<object>} The newly created user object.
   */
  async createUser(d1, userData) {
    const userId = crypto.randomUUID();
    const { email, authProvider, googleId, telegramId, firstName, username } = userData;
    const now = new Date().toISOString();

    const stmt = d1
      .prepare(
        `
            INSERT INTO users (id, email, authProvider, googleId, telegramId, firstName, username, createdAt, updatedAt)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
        `
      )
      .bind(
        userId,
        email || null,
        authProvider,
        googleId || null,
        telegramId || null,
        firstName || null,
        username || null,
        now,
        now
      );

    try {
      return await stmt.first();
    } catch (e) {
      console.error("D1 createUser Error:", e.message);
      throw new Error("Failed to create user.");
    }
  },

  /**
   * Updates a user's subscription information.
   * @param {D1Database} d1 - The D1 binding.
   * @param {string} userId - The ID of the user to update.
   * @param {object} subData - The new subscription data.
   * @returns {Promise<object>} The updated user object.
   */
  async updateUserSubscription(d1, userId, subData) {
    const { tier, expiresAt, providerSubscriptionId, hasUsedGoldTrial } = subData;
    const now = new Date().toISOString();

    const stmt = d1
      .prepare(
        `
            UPDATE users
            SET 
                subscription_tier = ?,
                subscription_expiresAt = ?,
                subscription_providerId = ?,
                subscription_hasUsedGoldTrial = ?,
                updatedAt = ?
            WHERE id = ?
            RETURNING *
        `
      )
      .bind(
        tier,
        expiresAt ? new Date(expiresAt).toISOString() : null,
        providerSubscriptionId || null,
        hasUsedGoldTrial ? 1 : 0,
        now,
        userId
      );
    try {
      return await stmt.first();
    } catch (e) {
      console.error("D1 updateUserSubscription Error:", e.message);
      throw new Error("Failed to update user subscription.");
    }
  },

  /**
   * Deletes a user and all their associated data (cascaded by foreign keys).
   * @param {D1Database} d1
   * @param {string} userId
   */
  async deleteUser(d1, userId) {
    try {
      // Because of `ON DELETE CASCADE` in table definitions, deleting a user
      // will automatically delete their ielts_exam_results and multilevel_exam_results.
      await d1.prepare("DELETE FROM users WHERE id = ?").bind(userId).run();
    } catch (e) {
      console.error("D1 deleteUser Error:", e.message);
      throw new Error("Failed to delete user.");
    }
  },

  // --- Admin Functions ---

  /**
   * Finds an admin by their email.
   * @param {D1Database} d1
   * @param {string} email
   * @returns {Promise<object|null>}
   */
  async findAdminByEmail(d1, email) {
    try {
      return await d1.prepare("SELECT * FROM admins WHERE email = ?").bind(email).first();
    } catch (e) {
      console.error("D1 findAdminByEmail Error:", e.message);
      return null;
    }
  },

  // --- Exam Result Functions ---

  /**
   * Creates a new multilevel exam result.
   * @param {D1Database} d1
   * @param {object} resultData
   * @returns {Promise<object>}
   */
  async createMultilevelExamResult(d1, resultData) {
    const resultId = crypto.randomUUID();
    const { userId, totalScore, feedbackBreakdown, transcript, examContent, practicedPart } =
      resultData;

    const stmt = d1
      .prepare(
        `
            INSERT INTO multilevel_exam_results (id, userId, totalScore, feedbackBreakdown, transcript, examContent, practicedPart)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id
        `
      )
      .bind(
        resultId,
        userId,
        totalScore,
        JSON.stringify(feedbackBreakdown),
        JSON.stringify(transcript),
        JSON.stringify(examContent),
        practicedPart
      );

    try {
      return await stmt.first(); // Returns { id: "..." }
    } catch (e) {
      console.error("D1 createMultilevelExamResult Error:", e.message);
      throw new Error("Failed to save multilevel exam result.");
    }
  },

  /**
   * Gets a user's multilevel exam history.
   * @param {D1Database} d1
   * @param {string} userId
   * @param {string} [retentionStartDateISO] - Optional ISO date string to filter history.
   * @returns {Promise<Array<object>>}
   */
  async getMultilevelExamHistory(d1, userId, retentionStartDateISO) {
    let query =
      "SELECT id, totalScore, createdAt, practicedPart FROM multilevel_exam_results WHERE userId = ?";
    const params = [userId];

    if (retentionStartDateISO) {
      query += " AND createdAt >= ?";
      params.push(retentionStartDateISO);
    }

    query += " ORDER BY createdAt DESC";

    try {
      const { results } = await d1
        .prepare(query)
        .bind(...params)
        .all();
      return results;
    } catch (e) {
      console.error("D1 getMultilevelExamHistory Error:", e.message);
      return [];
    }
  },

  /**
   * Gets full details for a single multilevel exam result.
   * @param {D1Database} d1
   * @param {string} resultId
   * @param {string} userId
   * @returns {Promise<object|null>}
   */
  async getMultilevelExamResultDetails(d1, resultId, userId) {
    try {
      const result = await d1
        .prepare("SELECT * FROM multilevel_exam_results WHERE id = ? AND userId = ?")
        .bind(resultId, userId)
        .first();

      // Parse JSON strings back into objects before returning
      if (result) {
        result.feedbackBreakdown = JSON.parse(result.feedbackBreakdown);
        result.transcript = JSON.parse(result.transcript);
        result.examContent = JSON.parse(result.examContent);
      }
      return result;
    } catch (e) {
      console.error("D1 getMultilevelExamResultDetails Error:", e.message);
      return null;
    }
  },

  // --- Token Functions ---

  /**
   * Creates a one-time token for Telegram login.
   * @param {D1Database} d1
   * @param {object} tokenData
   */
  async createOneTimeToken(d1, tokenData) {
    const { token, telegramId, botMessageId, userMessageId } = tokenData;
    try {
      await d1
        .prepare(
          "INSERT INTO one_time_tokens (token, telegramId, botMessageId, userMessageId) VALUES (?, ?, ?, ?)"
        )
        .bind(token, telegramId, botMessageId, userMessageId)
        .run();
    } catch (e) {
      console.error("D1 createOneTimeToken Error:", e.message);
      throw new Error("Failed to create one-time token.");
    }
  },

  /**
   * Finds and deletes a one-time token, ensuring it's used only once.
   * @param {D1Database} d1
   * @param {string} token
   * @returns {Promise<object|null>} The token object if found, otherwise null.
   */
  async findOneTimeTokenAndDelete(d1, token) {
    try {
      // D1 does not support transactions, so we perform this as two separate operations.
      // This is generally safe for this use case.
      const foundToken = await d1
        .prepare("SELECT * FROM one_time_tokens WHERE token = ?")
        .bind(token)
        .first();

      if (foundToken) {
        // Check for expiration manually
        const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000);
        if (new Date(foundToken.createdAt) < fiveMinutesAgo) {
          // Token expired, delete it and return null
          await d1.prepare("DELETE FROM one_time_tokens WHERE token = ?").bind(token).run();
          return null;
        }

        // Token is valid, delete it and return
        await d1.prepare("DELETE FROM one_time_tokens WHERE token = ?").bind(token).run();
        return foundToken;
      }

      return null;
    } catch (e) {
      console.error("D1 findOneTimeTokenAndDelete Error:", e.message);
      return null;
    }
  },

  // --- Word Bank Functions ---

  async getWordBankLevels(d1) {
    try {
      const { results } = await d1
        .prepare("SELECT DISTINCT cefrLevel FROM words ORDER BY cefrLevel ASC")
        .all();
      return results.map((r) => r.cefrLevel); // Extract just the level strings
    } catch (e) {
      console.error("D1 getWordBankLevels Error:", e.message);
      return [];
    }
  },

  async getWordBankTopics(d1, level) {
    try {
      const { results } = await d1
        .prepare("SELECT DISTINCT topic FROM words WHERE cefrLevel = ? ORDER BY topic ASC")
        .bind(level)
        .all();
      return results.map((r) => r.topic);
    } catch (e) {
      console.error("D1 getWordBankTopics Error:", e.message);
      return [];
    }
  },

  async getWordBankWords(d1, level, topic) {
    try {
      const { results } = await d1
        .prepare("SELECT * FROM words WHERE cefrLevel = ? AND topic = ?")
        .bind(level, topic)
        .all();
      return results;
    } catch (e) {
      console.error("D1 getWordBankWords Error:", e.message);
      return [];
    }
  },

  // --- Content Management Functions ---

  /**
   * Gets N random documents from a content table.
   * @param {D1Database} d1
   * @param {string} tableName - e.g., 'content_part1_1'
   * @param {number} size - The number of random documents to fetch.
   * @returns {Promise<Array<object>>}
   */
  async getRandomContent(d1, tableName, size) {
    try {
      // Note: Table name cannot be a bound parameter. Ensure tableName is not from user input.
      const { results } = await d1
        .prepare(`SELECT * FROM ${tableName} ORDER BY RANDOM() LIMIT ?`)
        .bind(size)
        .all();

      // Parse any JSON fields if necessary
      if (results.length > 0 && results[0].questions) {
        results.forEach((r) => (r.questions = JSON.parse(r.questions)));
      }
      if (results.length > 0 && results[0].forPoints) {
        results.forEach((r) => {
          r.forPoints = JSON.parse(r.forPoints);
          r.againstPoints = JSON.parse(r.againstPoints);
        });
      }

      return results;
    } catch (e) {
      console.error(`D1 getRandomContent for ${tableName} Error:`, e.message);
      return [];
    }
  },

  async createContent(d1, tableName, data) {
    try {
      const id = crypto.randomUUID();
      data.id = id;

      const columns = Object.keys(data);
      const values = Object.values(data).map((v) =>
        typeof v === "object" ? JSON.stringify(v) : v
      );

      const placeholders = columns.map(() => "?").join(", ");

      const stmt = d1
        .prepare(
          `INSERT INTO ${tableName} (${columns.join(", ")}) VALUES (${placeholders}) RETURNING *`
        )
        .bind(...values);

      return await stmt.first();
    } catch (e) {
      console.error(`D1 createContent for ${tableName} Error:`, e.message);
      throw new Error(`Failed to create content in ${tableName}`);
    }
  },

  /**
   * Updates a user's daily usage counters.
   * @param {D1Database} d1 - The D1 binding.
   * @param {string} userId - The ID of the user to update.
   * @param {object} usageData - The new usage counts and reset dates.
   */
  async updateUserUsage(d1, userId, usageData) {
    const { fullExams, partPractices } = usageData;
    const now = new Date().toISOString();
    try {
      await d1
        .prepare(
          `
                UPDATE users
                SET
                    dailyUsage_fullExams_count = ?,
                    dailyUsage_fullExams_lastReset = ?,
                    dailyUsage_partPractices_count = ?,
                    dailyUsage_partPractices_lastReset = ?,
                    updatedAt = ?
                WHERE id = ?
            `
        )
        .bind(
          fullExams.count,
          fullExams.lastReset,
          partPractices.count,
          partPractices.lastReset,
          now,
          userId
        )
        .run();
    } catch (e) {
      console.error("D1 updateUserUsage Error:", e.message);
      throw new Error("Failed to update user usage stats.");
    }
  },

  // --- IELTS Exam Functions ---

  /**
   * Creates a new IELTS exam result.
   * @param {D1Database} d1
   * @param {object} resultData - { userId, overallBand, criteria, transcript }
   * @returns {Promise<{id: string}>} The ID of the newly created result.
   */
  async createIeltsExamResult(d1, resultData) {
    const resultId = crypto.randomUUID();
    const { userId, overallBand, criteria, transcript } = resultData;

    try {
      const stmt = d1
        .prepare(
          `
                INSERT INTO ielts_exam_results (id, userId, overallBand, criteria, transcript)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
            `
        )
        .bind(resultId, userId, overallBand, JSON.stringify(criteria), JSON.stringify(transcript));
      return await stmt.first(); // Returns { id: "..." }
    } catch (e) {
      console.error("D1 createIeltsExamResult Error:", e.message);
      throw new Error("Failed to save IELTS exam result.");
    }
  },

  /**
   * Gets a user's IELTS exam history summary.
   * @param {D1Database} d1
   * @param {string} userId
   * @returns {Promise<Array<{id: string, overallBand: number, createdAt: string}>>}
   */
  async getIeltsExamHistory(d1, userId) {
    try {
      const { results } = await d1
        .prepare(
          "SELECT id, overallBand, createdAt FROM ielts_exam_results WHERE userId = ? ORDER BY createdAt DESC"
        )
        .bind(userId)
        .all();
      return results;
    } catch (e) {
      console.error("D1 getIeltsExamHistory Error:", e.message);
      return [];
    }
  },

  /**
   * Gets the full details for a single IELTS exam result.
   * @param {D1Database} d1
   * @param {string} resultId
   * @param {string} userId
   * @returns {Promise<object|null>} The full result object, or null if not found.
   */
  async getIeltsExamResultDetails(d1, resultId, userId) {
    try {
      const result = await d1
        .prepare("SELECT * FROM ielts_exam_results WHERE id = ? AND userId = ?")
        .bind(resultId, userId)
        .first();

      // Parse JSON strings back into objects before returning
      if (result) {
        result.criteria = JSON.parse(result.criteria);
        result.transcript = JSON.parse(result.transcript);
      }
      return result;
    } catch (e) {
      console.error("D1 getIeltsExamResultDetails Error:", e.message);
      return null;
    }
  },
};
