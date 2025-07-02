const express = require("express");
const router = express.Router();
const { protect } = require("../middleware/authMiddleware");
const { verifyAndGrantAccess, startGoldTrial } = require("../controllers/subscriptionController");

// All subscription routes should be protected
router.use(protect);

router.post("/verify-purchase", verifyAndGrantAccess);
// The start-trial route is good to keep
router.post("/start-trial", startGoldTrial);

module.exports = router;