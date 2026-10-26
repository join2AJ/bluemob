package com.bluemob.app.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bluemob.app.guide.GuideContent
import com.bluemob.app.ui.nav.Route
import com.bluemob.app.ui.nav.Shell

/** Guide articles, quizzes and downloadable packs. */
@Composable
fun GuideRoutes(route: Route.GuideArea, s: Shell) {
    val vm = s.vm
    when (route) {
        is Route.Article -> {
            val a = GuideContent.byId(route.id)
            val bookmarks = vm.bookmarks.collectAsStateWithLifecycle().value
            if (a == null) LaunchedEffect(Unit) { s.pop() }
            else ArticleScreen(a, a.id in bookmarks, s::pop, onToggleSaved = { vm.toggleBookmark(a.id) }, onSos = { s.push(Route.Sos) },
                onOpen = { s.push(Route.Article(it)) }, onQuiz = { s.push(Route.Quiz(a.id)) })
        }
        is Route.Quiz -> {
            val a = GuideContent.all().firstOrNull { it.id == route.id }
            if (a == null) LaunchedEffect(Unit) { s.pop() } else GuideQuizScreen(a, s::pop, onRead = s::pop)
        }
        Route.GuidePacks -> GuidePacksScreen(vm.bridgeUrl.collectAsStateWithLifecycle().value, vm.online.collectAsStateWithLifecycle().value, s::pop)
    }
}
