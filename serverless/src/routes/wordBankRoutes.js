// {PATH_TO_PROJECT}/src/routes/wordBankRoutes.js

import { Hono } from "hono";
import { getLevels, getTopics, getWords } from "../controllers/wordBankController";

const wordBankRoutes = new Hono();

wordBankRoutes.get("/levels", getLevels);
wordBankRoutes.get("/topics", getTopics);
wordBankRoutes.get("/words", getWords);

export default wordBankRoutes;
