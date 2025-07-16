// serverless/src/db/d1-client.js

export const db = {
  // --- User Functions ---

  async getUserById(d1, id) {
    try {
      return await d1.prepare("SELECT * FROM users WHERE id = ?").bind(id).first();
    } catch (e) {
      console.error("D1 getUserById Error:", e.message);
      return null;
    }
  },

  async findUserByProviderId(d1, { provider, id }) {
    let query;
    if (provider === "google") {
      query = "SELECT * FROM users WHERE googleId = ?";
    } else if (provider === "telegram") {
      query = "SELECT * FROM users WHERE telegramId = ?";
    } else {
      return null;
    }
    try {
      return await d1.prepare(query).bind(id).first();
    } catch (e) {
      console.error("D1 findUserByProviderId Error:", e.message);
      return null;
    }
  },

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

  async deleteUser(d1, userId) {
    try {
      await d1.prepare("DELETE FROM users WHERE id = ?").bind(userId).run();
    } catch (e) {
      console.error("D1 deleteUser Error:", e.message);
      throw new Error("Failed to delete user.");
    }
  },

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

  // --- Admin Functions ---

  async findAdminByEmail(d1, email) {
    try {
      return await d1.prepare("SELECT * FROM admins WHERE email = ?").bind(email).first();
    } catch (e) {
      console.error("D1 findAdminByEmail Error:", e.message);
      return null;
    }
  },

  // --- Token Functions ---

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

  async findOneTimeTokenAndDelete(d1, token) {
    try {
      const foundToken = await d1
        .prepare("SELECT * FROM one_time_tokens WHERE token = ?")
        .bind(token)
        .first();

      if (foundToken) {
        // Parse the UTC timestamp string correctly from D1
        const utcTimestampString = foundToken.createdAt.replace(" ", "T") + "Z";
        const createdAtDate = new Date(utcTimestampString);
        const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000);

        if (createdAtDate < fiveMinutesAgo) {
          await d1.prepare("DELETE FROM one_time_tokens WHERE token = ?").bind(token).run();
          return null; // Token is expired
        }

        // Token is valid, so delete it and return it
        await d1.prepare("DELETE FROM one_time_tokens WHERE token = ?").bind(token).run();
        return foundToken;
      }

      return null; // Token not found
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
      return results.map((r) => r.cefrLevel);
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

  async getRandomContent(d1, tableName, size) {
    try {
      const { results } = await d1
        .prepare(`SELECT * FROM ${tableName} ORDER BY RANDOM() LIMIT ?`)
        .bind(size)
        .all();

      // Automatically parse JSON fields if they exist
      if (results.length > 0) {
        results.forEach((r) => {
          if (r.questions) r.questions = JSON.parse(r.questions);
          if (r.forPoints) r.forPoints = JSON.parse(r.forPoints);
          if (r.againstPoints) r.againstPoints = JSON.parse(r.againstPoints);
          if (r.tags) r.tags = JSON.parse(r.tags);
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
      const dataWithId = { ...data, id };

      const columns = Object.keys(dataWithId);
      const values = Object.values(dataWithId);
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
};
