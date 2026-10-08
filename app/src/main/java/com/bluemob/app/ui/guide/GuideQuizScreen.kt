package com.bluemob.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bluemob.app.guide.Article
import com.bluemob.app.guide.GuideContent
import com.bluemob.app.guide.GuidePacks
import com.bluemob.app.guide.GuideQuiz
import com.bluemob.app.ui.components.SubScreen
import com.bluemob.app.ui.theme.Extra

/** "Test yourself" on one guide: tap an answer, see why, next. All right (or 2 of 3) counts as passed. */
@Composable
fun GuideQuizScreen(article: Article, onBack: () -> Unit, onRead: () -> Unit) {
    val questions = remember(article.id) { GuideQuiz.of(article, GuideContent.all()) }
    var index by rememberSaveable { mutableStateOf(0) }
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    var score by rememberSaveable { mutableStateOf(0) }
    val done = index >= questions.size
    val passed = done && score >= (questions.size * 2 + 2) / 3
    LaunchedEffect(passed) { if (passed) GuidePacks.markQuizPassed(article.id) }
    SubScreen("Test yourself", onBack) {
        item { Text(article.title, style = MaterialTheme.typography.titleMedium, color = Extra.ink2, modifier = Modifier.padding(top = 8.dp)) }
        if (done) item {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text(if (passed) "🏅" else "📖", style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("$score of ${questions.size} right", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text(if (passed) "Passed. You'd know what to do." else "Read it once more, then try again: it sticks better the second time.",
                    style = MaterialTheme.typography.bodyLarge, color = Extra.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Button(onClick = { index = 0; picked = null; score = 0 }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Try again") }
                androidx.compose.material3.TextButton(onClick = onRead, modifier = Modifier.fillMaxWidth()) { Text("Read the guide") }
            }
        } else {
            val q = questions[index]
            item { Text("Question ${index + 1} of ${questions.size}", style = MaterialTheme.typography.labelMedium, color = Extra.ink3, modifier = Modifier.padding(top = 12.dp)) }
            item { Text(q.prompt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 10.dp)) }
            q.options.forEachIndexed { i, o ->
                item {
                    val bg = when {
                        picked == null -> MaterialTheme.colorScheme.surface
                        i == q.answer -> Extra.pineTint
                        i == picked -> Extra.emberTint
                        else -> MaterialTheme.colorScheme.surface
                    }
                    Text((if (picked != null && i == q.answer) "✓  " else if (picked == i) "✗  " else "") + o, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 5.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg)
                            .clickable(enabled = picked == null) { picked = i; if (i == q.answer) score++ }.padding(14.dp))
                }
            }
            if (picked != null) {
                item {
                    Text((if (picked == q.answer) "Right. " else "Not quite. ") + q.why, style = MaterialTheme.typography.bodyMedium, color = Extra.ink2, modifier = Modifier.padding(top = 10.dp))
                }
                item { Button(onClick = { index++; picked = null }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text(if (index + 1 < questions.size) "Next" else "See result") } }
            }
        }
    }
}
