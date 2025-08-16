// {PATH_TO_PROJECT}/api/config/offerings.js

const OFFERINGS = {
  free: {
    dailyPartPractices: 3,
    dailyFullExams: 1,
    historyRetentionDays: 7,
    // For features that are unlimited, we can use a special value like -1 or Infinity
    monthlyFullExams: Infinity,
  },
  silver: {
    dailyPartPractices: Infinity, // Or just omit the key if you check for its existence
    dailyFullExams: Infinity,
    historyRetentionDays: 180, // ~6 months
    monthlyFullExams: 5,
  },
  // gold: {
  //   dailyPartPractices: Infinity,
  //   dailyFullExams: Infinity,
  //   historyRetentionDays: Infinity, // Represents unlimited
  //   monthlyFullExams: Infinity,
  // },
};

// We use `Infinity` for checks. It's cleaner than checking for null/undefined/-1.
// e.g., if (count >= OFFERINGS[tier].dailyPartPractices) { ... }
// If the limit is Infinity, this check will always be false.

module.exports = OFFERINGS;
