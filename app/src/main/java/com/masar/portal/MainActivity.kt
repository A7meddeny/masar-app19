package com.masar.portal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.masar.portal.data.Session
import com.masar.portal.model.MeResponse
import com.masar.portal.network.MasarApi
import com.masar.portal.ui.screens.*
import com.masar.portal.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app get() = application as MasarApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MasarTheme { AppRoot() } }
    }

    @Composable
    private fun AppRoot() {
        val scope = rememberCoroutineScope()
        var session by remember { mutableStateOf<Session?>(null) }
        var loaded by remember { mutableStateOf(false) }

        // اقرأ الجلسة المخزّنة عند الإقلاع
        LaunchedEffect(Unit) {
            session = app.session.snapshot()
            loaded = true
        }

        if (!loaded) {
            SplashLoader()
            return
        }

        val s = session ?: return
        when {
            // ١) لا رابط → شاشة الإعداد
            s.baseUrl.isNullOrBlank() -> SetupScreen(onConfigured = { url ->
                scope.launch {
                    app.session.saveBaseUrl(url)
                    session = app.session.snapshot()
                }
            })

            // ٢) لا توكن → شاشة تسجيل الدخول
            !s.isLoggedIn -> LoginScreen(
                baseUrl = s.baseUrl,
                onLogin = { token, drv ->
                    scope.launch {
                        app.session.saveLogin(token, drv.national_id, drv.name, drv.courier_id)
                        session = app.session.snapshot()
                    }
                },
                onChangeSite = {
                    scope.launch {
                        app.session.clearAll()
                        session = app.session.snapshot()
                    }
                }
            )

            // ٣) داخل التطبيق
            else -> MainShell(
                session = s,
                onLogout = {
                    scope.launch {
                        app.session.clearLogin()
                        session = app.session.snapshot()
                    }
                }
            )
        }
    }

    @Composable
    private fun MainShell(session: Session, onLogout: () -> Unit) {
        val scope = rememberCoroutineScope()
        var selectedTab by rememberSaveable { mutableStateOf(0) }
        var meData by remember { mutableStateOf<MeResponse?>(null) }
        var loading by remember { mutableStateOf(true) }
        var refreshTrigger by remember { mutableStateOf(0) }

        // اجلب بيانات المندوب
        LaunchedEffect(refreshTrigger) {
            loading = true
            try {
                val api = MasarApi(session.baseUrl!!)
                meData = api.fetchMe(session.nid!!, session.token!!)
            } catch (_: Exception) {
                meData = null
            }
            loading = false
        }

        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Ink2) {
                    val iformsCount = meData?.iforms_count ?: 0
                    listOf(
                        Triple(0, "نظرة عامة", Icons.Default.Home),
                        Triple(1, "الخدمات", Icons.Default.Build),
                        Triple(2, "النماذج", Icons.Default.Description),
                        Triple(3, "طلباتي", Icons.Default.History),
                        Triple(4, "حسابي", Icons.Default.AccountCircle),
                    ).forEach { (idx, label, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            icon = {
                                if (idx == 2 && iformsCount > 0) {
                                    BadgedBox(badge = {
                                        Badge(containerColor = BrandRed) {
                                            Text("$iformsCount", color = androidx.compose.ui.graphics.Color.White)
                                        }
                                    }) { Icon(icon, contentDescription = label) }
                                } else {
                                    Icon(icon, contentDescription = label)
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandRed,
                                selectedTextColor = BrandRed,
                                unselectedIconColor = TxtDim,
                                unselectedTextColor = TxtDim,
                                indicatorColor = Ink3,
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize().background(Ink)) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        baseUrl = session.baseUrl!!,
                        driverName = session.name ?: "",
                        nid = session.nid!!,
                        token = session.token!!,
                        data = meData,
                        loading = loading,
                    )
                    1 -> ServicesScreen(
                        baseUrl = session.baseUrl!!,
                        nid = session.nid!!,
                        token = session.token!!,
                        onSubmitted = { refreshTrigger++ },
                    )
                    2 -> IFormsScreen(
                        baseUrl = session.baseUrl!!,
                        nid = session.nid!!,
                        token = session.token!!,
                    )
                    3 -> HistoryScreen(data = meData, baseUrl = session.baseUrl ?: "")
                    4 -> ProfileScreen(
                        baseUrl = session.baseUrl!!,
                        data = meData,
                        driverName = session.name ?: "",
                        onLogout = onLogout,
                    )
                }
            }
        }
    }

    @Composable
    private fun SplashLoader() {
        Box(
            Modifier.fillMaxSize().background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = BrandRed)
        }
    }
}
