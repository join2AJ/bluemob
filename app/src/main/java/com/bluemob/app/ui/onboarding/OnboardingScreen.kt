package com.bluemob.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemob.app.identity.Identity
import com.bluemob.app.ui.components.Pill
import com.bluemob.app.ui.theme.Gradients
import com.bluemob.app.ui.theme.Space
import kotlinx.coroutines.launch

private data class Slide(val kind: HeroKind, val title: String, val body: String, val comingSoon: Boolean = false)

private val slides = listOf(
    Slide(
        HeroKind.OFF_GRID, "Talk freely, anywhere",
        "No towers. No internet. BlueMob links phones directly over Bluetooth and Wi-Fi: " +
            "in the mountains, at sea, or when the network goes down.",
    ),
    Slide(
        HeroKind.HOPS, "Every phone is a path",
        "Messages hop from friend to friend until they reach the right person. " +
            "The more people around, the further your voice travels.",
        comingSoon = true,
    ),
    Slide(
        HeroKind.BRIDGE, "One signal frees everyone",
        "If just one person nearby has internet, the whole group can reach the world through them.",
        comingSoon = true,
    ),
    Slide(
        HeroKind.RADAR, "See who's around",
        "A live radar shows who's online, how far away they are, and when you last saw them.",
    ),
    Slide(
        HeroKind.BUDDY, "Never alone",
        "Chat with Sky, your built-in buddy, to feel what talking off-grid is like before friends join.",
    ),
)

@Composable
fun OnboardingScreen(
    initialName: String,
    initialAvatar: String,
    onFinish: (name: String, avatar: String) -> Unit,
) {
    val pageCount = slides.size + 1
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var avatar by remember { mutableStateOf(initialAvatar) }
    val isLast = pager.currentPage == pageCount - 1

    Box(Modifier.fillMaxSize().background(Gradients.dawn())) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("BlueMob", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                if (!isLast) {
                    TextButton(onClick = { scope.launch { pager.animateScrollToPage(pageCount - 1) } }) { Text("Skip") }
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                if (page < slides.size) {
                    SlidePage(slides[page])
                } else {
                    ProfileSetupPage(name, avatar, onName = { name = it }, onAvatar = { avatar = it })
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(Space.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageDots(count = pageCount, current = pager.currentPage)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (isLast) onFinish(name, avatar)
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    enabled = !isLast || name.isNotBlank(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Text(if (isLast) "Start exploring" else "Next")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SlidePage(slide: Slide) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeroArt(slide.kind, Modifier.fillMaxWidth(0.9f).aspectRatio(1f))
        Spacer(Modifier.height(Space.xl))
        if (slide.comingSoon) {
            Pill("COMING SOON", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(Space.md))
        }
        Text(slide.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.md))
        Text(
            slide.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProfileSetupPage(name: String, avatar: String, onName: (String) -> Unit, onAvatar: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(Gradients.horizon()),
            contentAlignment = Alignment.Center,
        ) { Text(avatar, fontSize = 56.sp) }
        Spacer(Modifier.height(Space.xl))
        Text("Who are you out there?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        Text(
            "Pick a name and a spirit. People nearby will see these.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xl))
        OutlinedTextField(
            value = name,
            onValueChange = { onName(it.take(Identity.MAX_NAME_LENGTH)) },
            label = { Text("Your name") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.lg))
        AvatarPicker(selected = avatar, onSelect = onAvatar)
    }
}

@Composable
fun AvatarPicker(selected: String, onSelect: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = Modifier.fillMaxWidth().height(112.dp),
        userScrollEnabled = false,
    ) {
        items(Identity.AVATARS) { emoji ->
            val chosen = emoji == selected
            val bg by animateColorAsState(
                if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                label = "bg",
            )
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable { onSelect(emoji) },
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 24.sp) }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, label = "dot")
            val color by animateColorAsState(
                if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                label = "dotColor",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}
