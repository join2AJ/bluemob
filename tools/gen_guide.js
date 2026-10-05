// Generates app/src/main/java/com/bluemob/app/guide/GuideContent.kt from web/src/features.js.
// Run: node tools/gen_guide.js
const fs = require("fs");
const path = require("path");
const root = path.join(__dirname, "..");
const src = fs.readFileSync(path.join(root, "web/src/features.js"), "utf8");
const start = src.indexOf("  const A = (id, cat");
const end = src.indexOf("  const catOf");
const ARTICLES = new Function(src.slice(start, end) + "\nreturn ARTICLES;")();
const CAT = { aid: "FIRST_AID", water: "WATER", fire: "FIRE", shelter: "SHELTER", nav: "NAVIGATION", signal: "SIGNALS",
  weather: "WEATHER", disaster: "DISASTERS", basics: "BASICS" };
const q = (s) => JSON.stringify(s).replace(/\$/g, "\\$");
const list = (xs, ind) => xs.length ? `listOf(\n${xs.map((x) => `${ind}    ${q(x)},`).join("\n")}\n${ind})` : "emptyList()";
const body = ARTICLES.map((a) => {
  if (!CAT[a.cat]) throw new Error("Unknown category " + a.cat);
  return `        Article(
            id = ${q(a.id)},
            category = GuideCategory.${CAT[a.cat]},
            title = ${q(a.title)},
            minutes = ${a.mins},
            intro = ${q(a.intro)},
            steps = ${list(a.steps, "            ")},
            avoid = ${list(a.donts, "            ")},
        ),`;
}).join("\n");
const out = `package com.bluemob.app.guide

/*
 * GENERATED from web/src/features.js by \`node tools/gen_guide.js\`. Don't edit by hand.
 * General guidance written for BlueMob, not medical advice.
 */

/** One short survival guide, stored in the app so it works with no signal. */
data class Article(
    val id: String,
    val category: GuideCategory,
    val title: String,
    val minutes: Int,
    val intro: String,
    val steps: List<String>,
    val avoid: List<String>,
)

object GuideContent {
    val articles: List<Article> = listOf(
${body}
    )

    fun byId(id: String): Article? = articles.firstOrNull { it.id == id }
}
`;
fs.writeFileSync(path.join(root, "app/src/main/java/com/bluemob/app/guide/GuideContent.kt"), out);
console.log(ARTICLES.length + " articles");
