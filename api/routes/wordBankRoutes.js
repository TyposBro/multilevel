const express = require("express");
const { getLevels, getTopics, getWords } = require("../controllers/wordBankController");
const router = express.Router();

router.route("/levels").get(getLevels);
router.route("/topics").get(getTopics);
router.route("/words").get(getWords);

module.exports = router;
