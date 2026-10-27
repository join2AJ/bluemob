package com.bluemob.app.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bluemob.app.activity.Usage
import com.bluemob.app.guide.GuidePacks
import com.bluemob.app.guide.Streak
import com.bluemob.app.ui.account.AccountScreen
import com.bluemob.app.ui.account.AccountViewModel
import com.bluemob.app.ui.nav.Route
import com.bluemob.app.ui.nav.Shell
import com.bluemob.app.ui.system.AutoStartScreen
import com.bluemob.app.ui.system.BackupScreen
import com.bluemob.app.ui.system.BridgeScreen
import com.bluemob.app.ui.system.ConnectionsScreen
import com.bluemob.app.util.formatId
import kotlinx.coroutines.launch

/** The You tab's pages: people's profiles, account, settings and help. */
@Composable
fun YouRoutes(route: Route.YouArea, s: Shell) {
    val vm = s.vm
    val account: AccountViewModel = viewModel()
    val reports: ReportsViewModel = viewModel()
    val actions = s.actions
    val log by vm.log.collectAsStateWithLifecycle()
    val background by vm.background.collectAsStateWithLifecycle()
    when (route) {
        is Route.Person -> {
            val id = route.id
            val people by vm.people.collectAsStateWithLifecycle()
            val rescues by vm.rescues.collectAsStateWithLifecycle()
            val scores by vm.trustScores.collectAsStateWithLifecycle()
            val name by vm.name.collectAsStateWithLifecycle()
            val avatar by vm.avatar.collectAsStateWithLifecycle()
            val p = people.firstOrNull { it.nodeId == id }
            val isMe = id == vm.nodeId
            PersonScreen(id, if (isMe) name else p?.name ?: "Someone", if (isMe) avatar else p?.avatar, scores[id] ?: vm.scoreFor(id), isMe,
                remember(scores) { vm.myRatingsOf(id) }, rescues.firstOrNull { it.victimId == id }?.id, s::pop,
                onMessage = { s.nav.replace(Route.Chat(id)) }, onRate = { kind, ctx, remark -> vm.rate(id, kind, ctx, remark) },
                eligible = remember(id, scores) { vm.canRate(id) }, lastRatedAt = remember(id, scores) { vm.lastRatedAt(id) },
                onRateCategories = { stars, remark -> vm.rateCategories(id, stars, remark) },
                blocked = id in vm.blocked.collectAsStateWithLifecycle().value, onBlock = { vm.setBlocked(id, it) },
                onReport = { reason, text, msgs -> reports.reportPerson(id, reason, text, msgs) },
                verified = vm.isVerified(id, vm.verified.collectAsStateWithLifecycle().value), onVerify = { s.push(Route.Verify(id)) })
        }
        is Route.Verify -> {
            val id = route.id
            val all by vm.verified.collectAsStateWithLifecycle()
            val people by vm.people.collectAsStateWithLifecycle()
            VerifyScreen(people.firstOrNull { it.nodeId == id }?.name ?: "them", vm.isVerified(id, all), remember { vm.myVerifyCode() },
                remember(id, all) { vm.safetyNumber(id) }, s::pop, onScanned = { vm.onVerifyScanned(id, it) },
                onSetVerified = { vm.setVerified(id, it) }, askCamera = actions.requestCamera)
        }
        Route.EmergencyCard -> EmergencyCardScreen(vm.emergencyCard.collectAsStateWithLifecycle().value, vm::setEmergencyCard, vm::emergencyCardText, s::pop)
        Route.HelperSignals -> HelperSignalsScreen(vm.allowRemoteSignal.collectAsStateWithLifecycle().value, vm::setAllowRemoteSignal,
            vm.helperKinds.collectAsStateWithLifecycle().value, vm::setHelperKind, s::pop)
        Route.News -> NewsScreen(vm.news.items.collectAsStateWithLifecycle().value, vm.news.seenAt.collectAsStateWithLifecycle().value, s::pop, vm.news::markSeen)
        Route.Account -> AccountScreen(
            shortId = formatId(vm.nodeId),
            profile = account.profile.collectAsStateWithLifecycle().value, hasPin = account.pinSet.collectAsStateWithLifecycle().value,
            onDetails = account::setMedical, onEmail = account::setEmail, onTurnOffPin = account::clearPin,
            biometric = account.biometric.collectAsStateWithLifecycle().value, canUseBiometric = remember { actions.canUseBiometric() },
            lockAfterMs = account.lockAfterMs.collectAsStateWithLifecycle().value, recoverySaved = account.recoverySaved.collectAsStateWithLifecycle().value,
            onBack = s::pop, onBiometric = { on -> if (on) actions.biometricUnlock { account.setBiometric(true) } else account.setBiometric(false) },
            onLockAfter = account::setLockAfter, onLockNow = account::lockNow, checkPin = account::checkPin, onNewPin = account::setPin,
            recoveryCode = account::recoveryCode, onRecoverySaved = account::setRecoverySaved,
            onBackup = { s.push(Route.Backup) },
            onDeleteAccount = { phoneOnly -> account.deleteAccount(phoneOnly) },
            onExportData = { s.scope.launch { actions.shareFiles(listOf(account.exportMyData())) } },
            privacyUrl = vm.privacyUrl(),
        )
        Route.Connections -> ConnectionsScreen(vm.radios.collectAsStateWithLifecycle().value, vm.online.collectAsStateWithLifecycle().value, s::pop,
            onSwitch = actions.switchRadio, background = background, onBackground = { on ->
                vm.setBackground(on)
                // Turning it on asks Android once to let BlueMob run with Battery Saver on; turning it off never asks.
                if (on && !s.system.keepsRunning) actions.askKeepRunning()
            })
        Route.AutoStart, is Route.Power -> AutoStartScreen(
            vm.autoMesh.collectAsStateWithLifecycle().value, vm.bluetoothPolicy.collectAsStateWithLifecycle().value,
            vm.wifiPolicy.collectAsStateWithLifecycle().value, background, vm.shareLocation.collectAsStateWithLifecycle().value,
            s::pop, vm::setMeshAtStart, vm::setBluetoothPolicy, vm::setWifiPolicy, vm::setBackground, s.toggleLocation,
            running = vm.running.collectAsStateWithLifecycle().value, onToggleMesh = { if (it) vm.startMesh() else vm.stopMesh() },
            keepsRunning = s.system.keepsRunning, onKeepRunning = actions.askKeepRunning,
            onConnections = { s.push(Route.Connections) }, onBatterySaver = actions.openBatterySaver,
            batterySaver = s.system.batterySaver, highlight = (route as? Route.Power)?.highlight ?: "",
        )
        Route.Audit -> {
            val audit by vm.audit.collectAsStateWithLifecycle()
            val witnesses by vm.witnesses.collectAsStateWithLifecycle()
            AuditScreen(audit.first, audit.second, s::pop, witnesses, vm.auditPublicKey, remember(audit.first.size) { vm.securityStatus() })
        }
        Route.Badges -> BadgesScreen(vm.game.collectAsStateWithLifecycle().value, s::pop)
        Route.Activity -> {
            val events by vm.activity.collectAsStateWithLifecycle()
            val people by vm.people.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { Usage.flush() }
            val guidesRead = GuidePacks.read.collectAsStateWithLifecycle().value.size
            val quizzes = GuidePacks.quizzesPassed.collectAsStateWithLifecycle().value.size
            val streak = Streak.of(GuidePacks.readDays.collectAsStateWithLifecycle().value)
            ActivityScreen(events, s::pop,
                names = people.associate { it.nodeId to it.name } + vm.groups.collectAsStateWithLifecycle().value.mapValues { it.value.name },
                usage = Usage.days.collectAsStateWithLifecycle().value,
                learning = Triple(guidesRead, quizzes, streak))
        }
        Route.Bridge -> {
            val st by vm.bridgeStatus.collectAsStateWithLifecycle()
            val url by vm.bridgeUrl.collectAsStateWithLifecycle()
            BridgeScreen(st, url, s::pop, onSave = vm::setBridgeUrl, builtIn = vm.builtInRelay, liveConnected = vm.liveConnected.collectAsStateWithLifecycle().value)
        }
        Route.Backup -> BackupScreen(vm.backups, vm.backups.status.collectAsStateWithLifecycle().value,
            vm.backups.every.collectAsStateWithLifecycle().value, vm.backups.folder.collectAsStateWithLifecycle().value, actions.backup, s::pop)
        Route.Storage -> {
            val conversations by vm.conversations.collectAsStateWithLifecycle()
            StorageScreen(
                messages = conversations.values.sumOf { it.size }, files = conversations.values.sumOf { l -> l.count { it.att.isNotEmpty() } },
                people = vm.people.collectAsStateWithLifecycle().value.size,
                onBack = s::pop, onForgetPeople = vm::forgetPeople, onClearMessages = vm::clearMessages, onBackup = { s.push(Route.Backup) },
            )
        }
        Route.Diagnostics -> DiagnosticsScreen(
            vm.techDetails.collectAsStateWithLifecycle().value, vm::setTechDetails, log, remember { vm.lastError() },
            onBack = s::pop, onBridge = { s.push(Route.Bridge) }, onAudit = { s.push(Route.Audit) },
            onReport = { text, details -> reports.reportProblem(text, details, log) },
            onOpenReport = { s.push(Route.Report) },
            autoReports = vm.autoErrorReports.collectAsStateWithLifecycle().value, onAutoReports = vm::setAutoErrorReports,
        )
        Route.Report -> ReportScreen(s::pop,
            onAddPhoto = { done -> actions.pickFile("photo") { uri -> reports.reportPhoto(uri, done) } },
            onSend = { c, sub, text, details, photos -> reports.reportProblem(text, details, log, c, sub, photos) })
    }
}
