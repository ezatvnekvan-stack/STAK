package app.stak.nativeapp.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape

import app.stak.nativeapp.ui.components.glassChrome







import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.imePadding

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.ui.platform.LocalFocusManager

import androidx.compose.ui.platform.LocalSoftwareKeyboardController

import androidx.compose.foundation.background



import androidx.compose.foundation.border



import androidx.compose.foundation.clickable



import androidx.compose.foundation.layout.Arrangement



import androidx.compose.foundation.layout.Box



import androidx.compose.foundation.layout.Column



import androidx.compose.foundation.layout.PaddingValues



import androidx.compose.foundation.layout.Row



import androidx.compose.foundation.layout.Spacer



import androidx.compose.foundation.layout.fillMaxSize



import androidx.compose.foundation.layout.fillMaxWidth



import androidx.compose.foundation.layout.height



import androidx.compose.foundation.layout.heightIn



import androidx.compose.foundation.layout.padding



import androidx.compose.foundation.layout.size



import androidx.compose.foundation.layout.width



import androidx.compose.foundation.lazy.LazyColumn



import androidx.compose.foundation.lazy.grid.GridCells



import androidx.compose.foundation.lazy.grid.GridItemSpan



import androidx.compose.foundation.lazy.grid.LazyVerticalGrid



import androidx.compose.foundation.lazy.grid.items



import androidx.compose.foundation.shape.CircleShape





import androidx.compose.material.icons.Icons



import androidx.compose.material.icons.automirrored.filled.ArrowBack



import androidx.compose.material.icons.filled.Check



import androidx.compose.material3.Icon



import androidx.compose.material3.Text



import androidx.compose.runtime.Composable



import androidx.compose.runtime.collectAsState



import androidx.compose.runtime.getValue



import androidx.compose.runtime.mutableStateOf



import androidx.compose.runtime.remember



import androidx.compose.runtime.setValue



import androidx.compose.ui.Alignment



import androidx.compose.ui.Modifier



import androidx.compose.ui.draw.clip



import androidx.compose.ui.draw.drawBehind



import androidx.compose.ui.graphics.Brush



import androidx.compose.ui.graphics.Color



import androidx.compose.ui.semantics.contentDescription



import androidx.compose.ui.semantics.semantics



import androidx.compose.ui.text.font.FontWeight



import androidx.compose.ui.unit.dp



import androidx.compose.ui.unit.sp



import app.stak.nativeapp.ui.components.StakButton



import app.stak.nativeapp.ui.components.StakButtonVariant



import app.stak.nativeapp.ui.components.StakPageHeader



import app.stak.nativeapp.ui.components.StakTextField



import app.stak.nativeapp.ui.theme.DotoFont



import app.stak.nativeapp.ui.theme.HankenFont



import app.stak.nativeapp.ui.theme.Lucide



import app.stak.nativeapp.ui.theme.SpaceMonoFont



import app.stak.nativeapp.ui.theme.StakAccent



import app.stak.nativeapp.ui.theme.StakText



import app.stak.nativeapp.ui.theme.StakTextMuted







private data class SettingsCard(



    val id: String,



    val icon: androidx.compose.ui.graphics.vector.ImageVector,



    val title: String,



    val summary: String



)







private val settingsCards = listOf(



    SettingsCard("account", Lucide.Key, "TorBox account", "No key"),



    SettingsCard("metadata", Lucide.Database, "Metadata & ratings", "TMDb · Cinemeta · OMDb"),





    SettingsCard("sources", Lucide.Radio, "Search sources", "Torrentio · AIOStreams · custom addons"),



    SettingsCard("jellyfin", Lucide.MonitorPlay, "Jellyfin", "Server · playback · sync"),



    SettingsCard("appearance", Lucide.Palette, "Appearance", "Theme · accent · atmosphere · ratings"),



    SettingsCard("home", Lucide.Grid, "Home & browsing", "Shelves · navigation · genres"),



    SettingsCard("intelligence", Lucide.MonitorPlay, "Zurg browser", "Browse files"),

    SettingsCard("setup", Lucide.Check, "Setup assistant", "Connections · sources · ready"),



    SettingsCard("data", Lucide.Shield, "Data & app", "Backup · tools · about STAK")



)







@Composable



fun AccountScreen(viewModel: AccountViewModel, initialSection: String? = null, onSetup:()->Unit={},onMediaCenter:()->Unit={}) {



    val serverUrl by viewModel.serverUrl.collectAsState()



    val torboxKey by viewModel.torboxKey.collectAsState()



    val tmdbKey by viewModel.tmdbKey.collectAsState()



    val omdbKey by viewModel.omdbKey.collectAsState()



    val jellyfinUrl by viewModel.jellyfinUrl.collectAsState()



    val connection by viewModel.connection.collectAsState()



    val message by viewModel.message.collectAsState()



    val accent by viewModel.accent.collectAsState()



    val reducedMotion by viewModel.reducedMotion.collectAsState()



    val haptics by viewModel.haptics.collectAsState()



    val ambient by viewModel.ambient.collectAsState()

    val dailyReminder by viewModel.dailyReminder.collectAsState()

    // Android 13+ needs a runtime permission before the reminder can be posted.

    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(

        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()

    ) { granted -> if (granted) viewModel.setDailyReminder(true) }

    val requestReminder: (Boolean) -> Unit = { enabled ->

        if (!enabled) viewModel.setDailyReminder(false)

        else if (android.os.Build.VERSION.SDK_INT < 33 ||

            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==

            android.content.pm.PackageManager.PERMISSION_GRANTED

        ) viewModel.setDailyReminder(true)

        else permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)

    }







    var section by remember(initialSection) { mutableStateOf(initialSection) }







    androidx.activity.compose.BackHandler(enabled = section != null) { section = null }

    if(section=="sources"){SourcesSettings(onBack={section=null});return}


    if (section in setOf("account", "metadata", "jellyfin")) {

        ProviderSettings(section!!, viewModel.services, onBack = { section = null })

        return

    }

    if (section != null) {



        SettingsDetail(



            id = section!!,



            viewModel = viewModel,



            serverUrl = serverUrl,



            torboxKey = torboxKey,



            tmdbKey = tmdbKey,



            omdbKey = omdbKey,



            jellyfinUrl = jellyfinUrl,



            connection = connection,



            message = message,



            accent = accent,



            reducedMotion = reducedMotion,



            haptics = haptics,



            ambient = ambient,



            dailyReminder = dailyReminder,



            onDailyReminder = requestReminder,



            onTestReminder = { viewModel.sendTestReminder() },



            onBack = { section = null }



        )



        return



    }







    LazyVerticalGrid(



        columns = GridCells.Fixed(1),



        modifier = Modifier.fillMaxSize(),



        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 120.dp),



        horizontalArrangement = Arrangement.spacedBy(10.dp),



        verticalArrangement = Arrangement.spacedBy(10.dp)



    ) {



        item(span = { GridItemSpan(maxLineSpan) }) {



            Column {



                StakPageHeader(title = "Settings")



                Spacer(Modifier.height(20.dp))



                ConnectionsCard(



                    torboxKey = torboxKey,



                    jellyfinUrl = jellyfinUrl,



                    connection = connection,



                    onTest = { viewModel.testConnection() },



                    onOpen = { section = it }



                )



                Spacer(Modifier.height(8.dp))



            }



        }



        items(



            settingsCards.map { card ->



                when (card.id) {



                    "account" -> card.copy(summary = if (torboxKey.isBlank()) "No key" else "Key saved")



                    "metadata" -> card



                    "jellyfin" -> card



                    else -> card



                }



            },



            key = { it.id }



        ) { card ->



            SettingsIndexCard(card, onClick = { if(card.id=="setup")onSetup() else if(card.id=="intelligence")onMediaCenter() else section = card.id })



        }



    }



}







@Composable



private fun ConnectionsCard(



    torboxKey: String,



    jellyfinUrl: String,



    connection: ConnectionState,



    onTest: () -> Unit,



    onOpen: (String) -> Unit



) {



    Column(



        modifier = Modifier



            .fillMaxWidth()



            .clip(RoundedCornerShape(14.dp))



            .glassChrome(14.dp)



            .border(1.dp, Color(0x21FFFFFF), RoundedCornerShape(14.dp))



            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)



    ) {



        Row(



            modifier = Modifier.fillMaxWidth().height(44.dp),



            horizontalArrangement = Arrangement.SpaceBetween,



            verticalAlignment = Alignment.CenterVertically



        ) {



            Text(



                "CONNECTIONS",



                fontFamily = SpaceMonoFont,



                fontWeight = FontWeight.Medium,



                fontSize = 10.sp,



                letterSpacing = 0.8.sp,



                color = StakTextMuted



            )



            Box(



                modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onTest() },



                contentAlignment = Alignment.Center



            ) {



                Icon(Lucide.Refresh, contentDescription = "Test connections", tint = StakText, modifier = Modifier.size(15.dp))



            }



        }



        ConnectionRow(



            label = "TorBox",



            detail = if (torboxKey.isBlank()) "Not verified" else "Key saved",



            ok = torboxKey.isNotBlank(),



            onClick = { onOpen("account") }



        )



        ConnectionRow(



            label = "Jellyfin",



            detail = if (jellyfinUrl.isBlank()) "Not configured" else "Configured",



            ok = jellyfinUrl.isNotBlank(),



            onClick = { onOpen("jellyfin") }



        )



        ConnectionRow(



            label = "Sources",



            detail = "${app.stak.nativeapp.data.StreamAddonStore(app.stak.nativeapp.data.NativeRuntime.settings).all().count{it.enabled}} enabled",



            ok = app.stak.nativeapp.data.StreamAddonStore(app.stak.nativeapp.data.NativeRuntime.settings).all().any{it.enabled},



            onClick = { onOpen("sources") }



        )



    }



}







@Composable



private fun ConnectionRow(label: String, detail: String, ok: Boolean, onClick: () -> Unit) {



    Row(



        modifier = Modifier



            .fillMaxWidth()



            .heightIn(min = 48.dp)



            .drawBehind {



                val hair = 1.dp.toPx()



                drawLine(



                    color = Color(0x1FFFFFFF),



                    start = androidx.compose.ui.geometry.Offset(0f, hair / 2f),



                    end = androidx.compose.ui.geometry.Offset(size.width, hair / 2f),



                    strokeWidth = hair



                )



            }



            .clickable { onClick() },



        verticalAlignment = Alignment.CenterVertically,



        horizontalArrangement = Arrangement.spacedBy(10.dp)



    ) {



        Box(



            Modifier



                .size(6.dp)



                .clip(CircleShape)



                .background(if (ok) Color(0xFF10B981) else Color(0xFFA3A3A3))



        )



        Text(label, fontFamily = HankenFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = StakText)



        Spacer(Modifier.weight(1f))



        Text(



            detail,



            fontFamily = SpaceMonoFont,



            fontSize = 10.sp,



            color = Color(0xFFA3A3A3)



        )



        Icon(Lucide.ChevronRight, contentDescription = null, tint = Color(0xFF909090), modifier = Modifier.size(15.dp))



    }



}







@Composable

private fun SettingsIndexCard(card: SettingsCard, onClick: () -> Unit) {

    app.stak.nativeapp.ui.components.ServiceRow(card.title,card.summary,card.icon,onClick=onClick)

}



@Composable



private fun SettingsDetail(



    id: String,



    viewModel: AccountViewModel,



    serverUrl: String,



    torboxKey: String,



    tmdbKey: String,



    omdbKey: String,



    jellyfinUrl: String,



    connection: ConnectionState,



    message: String?,



    accent: Long,



    reducedMotion: Boolean,



    haptics: Boolean,



    ambient: Boolean,



    dailyReminder: Boolean,



    onDailyReminder: (Boolean) -> Unit,



    onTestReminder: () -> Unit,



    onBack: () -> Unit



) {



    val card = settingsCards.first { it.id == id }



    Column(



        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 120.dp)



    ) {



        Column {



            Row(



                modifier = Modifier



                    .fillMaxWidth()



                    .heightIn(min = 48.dp)



                    .clickable { onBack() },



                verticalAlignment = Alignment.CenterVertically



            ) {



                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = StakTextMuted, modifier = Modifier.size(16.dp))



                Spacer(Modifier.width(10.dp))



                Text(



                    card.title.uppercase(),



                    fontFamily = SpaceMonoFont,



                    fontWeight = FontWeight.Medium,



                    fontSize = 10.sp,



                    letterSpacing = 0.8.sp,



                    color = StakTextMuted



                )



            }



            Spacer(Modifier.height(12.dp))



            Text(



                card.title,



                fontFamily = DotoFont,



                fontWeight = FontWeight.Black,



                fontSize = 28.sp,



                lineHeight = 32.sp,



                letterSpacing = (-1).sp,



                color = StakText



            )



            Spacer(Modifier.height(16.dp))



            Column(



                modifier = Modifier



                    .fillMaxWidth()



                    .clip(RoundedCornerShape(18.dp))



                    .glassChrome(14.dp)



                    .border(1.dp, Color(0x21FFFFFF), RoundedCornerShape(18.dp))



                    .padding(16.dp)



            ) {



                when (id) {



                    "appearance" -> {



                        AppearancePreview(accent)



                        Spacer(Modifier.height(18.dp))



                        FieldLabel("Accent")



                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {



                            listOf(



                                0xFFD71921 to "Red",



                                0xFFE9B949 to "Amber",



                                0xFF4ADE80 to "Green",



                                0xFF5A78FF to "Blue",

                                0xFFFFFFFF to "White"



                            ).forEach { (color, label) ->



                                val selected = color == accent



                                Box(



                                    modifier = Modifier



                                        .size(38.dp)



                                        .clip(CircleShape)



                                        .background(Color(color))



                                        .border(if (selected) 2.dp else 1.dp, if (selected) StakText else Color(0x33FFFFFF), CircleShape)



                                        .clickable { viewModel.setAccent(color) }



                                        .semantics { contentDescription = "Accent $label" },



                                    contentAlignment = Alignment.Center



                                ) {



                                    if (selected) {



                                        Icon(Icons.Filled.Check, contentDescription = label, tint = if(color==0xFFFFFFFFL)Color.Black else Color.White, modifier = Modifier.size(16.dp))



                                    }



                                }



                            }



                        }



                        Spacer(Modifier.height(18.dp))



                        SettingsToggle("Atmosphere", "Ambient mesh and dot field", ambient) { viewModel.setAmbient(it) }



                        Spacer(Modifier.height(14.dp))



                        SettingsToggle("Reduced motion", "Fewer transitions and no autoplay motion", reducedMotion) { viewModel.setReducedMotion(it) }



                        Spacer(Modifier.height(14.dp))



                        SettingsToggle("Haptic feedback", "Vibrate on navigation and save actions", haptics) { viewModel.setHaptics(it) }



                        Spacer(Modifier.height(14.dp))



                        SettingsToggle("Daily reminder", "Around 19:00 local time · at most once a day", dailyReminder, onDailyReminder)



                        if (dailyReminder) {



                            Spacer(Modifier.height(10.dp))



                            StakButton("Send a test notification", onTestReminder, variant = StakButtonVariant.Ghost, compact = true)



                        }



                    }







                    "home" -> {



                        Text("Home shelves", fontFamily = HankenFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = StakText)



                        Spacer(Modifier.height(12.dp))



                        val rows by viewModel.homeSections.collectAsState()



                        rows.forEachIndexed { index, row ->



                            SettingsToggle(row.title, "", row.enabled) { viewModel.setHomeSection(row.id, it) }



                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {



                                StakButton("Move up", { viewModel.moveHomeSection(row.id, -1) }, enabled = index > 0, compact = true)



                                StakButton("Move down", { viewModel.moveHomeSection(row.id, 1) }, enabled = index < rows.lastIndex, compact = true)



                            }



                            Spacer(Modifier.height(12.dp))



                        }



                        Spacer(Modifier.height(16.dp))

                        Text("Navigation", fontFamily = HankenFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = StakText)

                        val navigation by viewModel.navigation.collectAsState()

                        val all = navigation + app.stak.nativeapp.data.NavigationPreferences.allowed.filter { it !in navigation }

                        all.forEach { nav ->

                            val label = if (nav == "account") "Settings" else if (nav == "torbox") "TorBox" else nav.replaceFirstChar { it.uppercase() }

                            if (nav !in listOf("home", "account")) SettingsToggle(label, "", nav in navigation) { viewModel.setNavigation(nav, it) }

                            else Text(label, fontFamily = HankenFont, fontSize = 14.sp, color = StakText, modifier = Modifier.padding(vertical = 12.dp))

                            if (nav in navigation) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                                StakButton("Move left", { viewModel.moveNavigation(nav, -1) }, enabled = navigation.indexOf(nav) > 0, compact = true)

                                StakButton("Move right", { viewModel.moveNavigation(nav, 1) }, enabled = navigation.indexOf(nav) < navigation.lastIndex, compact = true)

                            }

                        }

                    }







                    "data" -> {



                        Text("Catalog requests connect directly to Cinemeta or TMDb. Saved titles and viewing progress are stored on this device.", fontFamily = HankenFont, fontSize = 13.sp, lineHeight = 19.sp, color = StakTextMuted)



                        Spacer(Modifier.height(18.dp))



                        Text(



                            "STAK NATIVE · 1.0\nKotlin / Compose port of the STAK Capacitor experience.",



                            fontFamily = SpaceMonoFont,



                            fontSize = 9.sp,



                            lineHeight = 14.sp,



                            letterSpacing = 0.5.sp,



                            color = StakTextMuted



                        )



                    }



                }



            }



        }



    }



}







private fun monoStyle() = androidx.compose.ui.text.TextStyle(



    fontFamily = SpaceMonoFont,



    fontSize = 12.sp,



    color = StakText



)







@Composable



private fun AppearancePreview(accent: Long) {



    Column(



        modifier = Modifier



            .fillMaxWidth()



            .clip(RoundedCornerShape(16.dp))



            .background(



                Brush.radialGradient(



                    colors = listOf(Color(accent).copy(alpha = 0.14f), Color.Transparent),



                    radius = 700f



                )



            )



            .background(Color(0xFF080808))



            .border(1.dp, Color(0x21FFFFFF), RoundedCornerShape(16.dp))



            .padding(18.dp)



    ) {



        Text("STAK", fontFamily = DotoFont, fontWeight = FontWeight.Black, fontSize = 27.sp, color = StakText)



        Spacer(Modifier.height(4.dp))



        Text("NATIVE APPEARANCE", fontFamily = SpaceMonoFont, fontSize = 9.sp, letterSpacing = 0.8.sp, color = StakTextMuted)



        Spacer(Modifier.height(14.dp))



        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {



            repeat(3) { index ->



                Box(



                    modifier = Modifier



                        .width(34.dp)



                        .height(48.dp)



                        .clip(RoundedCornerShape(6.dp))



                        .background(



                            Brush.linearGradient(



                                listOf(Color(0x26FFFFFF), Color.Transparent)



                            )



                        )



                        .border(1.dp, if (index == 0) Color(accent) else Color(0x21FFFFFF), RoundedCornerShape(6.dp))



                )



            }



        }



        Spacer(Modifier.height(12.dp))



        Text("ACCENT", fontFamily = SpaceMonoFont, fontSize = 9.sp, letterSpacing = 0.8.sp, color = StakTextMuted)



    }



}







@Composable



private fun ProviderRow(title: String, subtitle: String, active: Boolean) {



    Row(



        modifier = Modifier



            .fillMaxWidth()



            .padding(vertical = 12.dp)



            .drawBehind {



                val hair = 1.dp.toPx()



                drawLine(



                    color = Color(0x1FFFFFFF),



                    start = androidx.compose.ui.geometry.Offset(0f, hair / 2f),



                    end = androidx.compose.ui.geometry.Offset(size.width, hair / 2f),



                    strokeWidth = hair



                )



            },



        verticalAlignment = Alignment.CenterVertically



    ) {



        Column(modifier = Modifier.weight(1f)) {



            Text(title, fontFamily = HankenFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = StakText)



            Spacer(Modifier.height(2.dp))



            Text(subtitle, fontFamily = HankenFont, fontSize = 11.sp, color = StakTextMuted)



        }



        Box(



            Modifier



                .size(6.dp)



                .clip(CircleShape)



                .background(if (active) Color(0xFF10B981) else Color(0xFF909090))



        )



    }



}







@Composable



private fun FieldLabel(text: String) {



    Text(text, fontFamily = HankenFont, fontSize = 11.sp, color = StakTextMuted)



    Spacer(Modifier.height(5.dp))



}







@Composable



private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {



    Row(



        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },



        verticalAlignment = Alignment.CenterVertically



    ) {



        Column(modifier = Modifier.weight(1f)) {



            Text(title, fontFamily = HankenFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = StakText)



            Spacer(Modifier.height(2.dp))



            Text(subtitle, fontFamily = HankenFont, fontSize = 11.sp, color = StakTextMuted)



        }



        Box(



            modifier = Modifier



                .width(44.dp)



                .height(26.dp)



                .clip(CircleShape)



                .background(if (checked) StakAccent else Color(0xFF262626))



                .padding(3.dp)



        ) {



            Box(



                modifier = Modifier



                    .size(20.dp)



                    .clip(CircleShape)



                    .background(if(checked)app.stak.nativeapp.ui.theme.StakOnAccent else Color.White)



                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)



            )



        }



    }



}



