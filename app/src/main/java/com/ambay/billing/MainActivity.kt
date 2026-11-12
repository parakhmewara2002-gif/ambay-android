package com.ambay.billing

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.ambay.billing.core.UserRole
import com.ambay.billing.data.local.SecurePrefs
import com.ambay.billing.data.remote.GitHubUpdateClient
import com.ambay.billing.ui.auth.AuthViewModel
import com.ambay.billing.ui.auth.FirstLaunchWizardScreen
import com.ambay.billing.ui.auth.LoginScreen
import com.ambay.billing.ui.owner.OwnerMainScreen
import com.ambay.billing.ui.staff.StaffMainScreen
import com.ambay.billing.ui.theme.AmbayTheme
import com.ambay.billing.ui.theme.LivingGlassBackground
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeState
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var securePrefs: SecurePrefs
    @Inject lateinit var updateClient: GitHubUpdateClient

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AmbayTheme {
                val authVm: AuthViewModel = hiltViewModel()
                val authUi by authVm.ui.collectAsState()
                val hazeState = remember { HazeState() }
                var hasUpdateBadge by remember {
                    mutableStateOf(
                        getSharedPreferences("ambay_update_badge", Context.MODE_PRIVATE)
                            .getBoolean("has_update", false)
                    )
                }

                // Auto-lock check when returning from background
                LaunchedEffect(Unit) {
                    val lastBg = securePrefs.getLastBackgroundTs()
                    val lockMin = securePrefs.getAutoLockMinutes()
                    if (lastBg > 0L && lockMin > 0 && System.currentTimeMillis() - lastBg > lockMin * 60_000L) {
                        authVm.logout()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LivingGlassBackground()

                    Crossfade(
                        targetState = Triple(authUi.wizardDone, authUi.loggedInRole, Unit),
                        label = "root_nav"
                    ) { (wizardDone, role, _) ->
                        when {
                            !wizardDone -> FirstLaunchWizardScreen(
                                ui = authUi,
                                hazeState = hazeState,
                                vm = authVm
                            )
                            role == null -> LoginScreen(
                                ui = authUi,
                                hazeState = hazeState,
                                vm = authVm
                            )
                            role == UserRole.STAFF -> StaffMainScreen(
                                hazeState = hazeState,
                                hasUpdateBadge = hasUpdateBadge,
                                updateClient = updateClient,
                                onLogout = { authVm.logout() }
                            )
                            role == UserRole.OWNER -> OwnerMainScreen(
                                hazeState = hazeState,
                                hasUpdateBadge = hasUpdateBadge,
                                updateClient = updateClient,
                                onSwitchToStaff = { authVm.switchToStaffView() },
                                onLogout = { authVm.logout() }
                            )
                        }
                    }
                }
            }
        }
    }
}
