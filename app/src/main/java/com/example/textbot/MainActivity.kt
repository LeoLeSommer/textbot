package com.example.textbot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.textbot.ui.screens.ConversationDetailScreen
import com.example.textbot.ui.screens.ConversationListScreen
import com.example.textbot.ui.viewmodel.SmsViewModel
import com.example.textbot.ui.theme.TextBotTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SmsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        createNotificationChannel()
        enableEdgeToEdge()

        setContent {
            TextBotTheme {
                var permissionsGranted by remember {
                    mutableStateOf(
                        hasPermission(Manifest.permission.READ_SMS) &&
                                hasPermission(Manifest.permission.READ_CONTACTS) &&
                                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                    )
                }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    permissionsGranted = permissions.values.all { it }
                    if (!permissionsGranted) {
                        Toast.makeText(this, getString(R.string.error_permissions_required), Toast.LENGTH_LONG).show()
                    }
                }

                val roleLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { _ ->
                    // Handle role request result if needed
                }

                fun checkDefaultSmsRole() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = getSystemService(RoleManager::class.java)
                        if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true &&
                            !roleManager.isRoleHeld(RoleManager.ROLE_SMS)
                        ) {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                            roleLauncher.launch(intent)
                        }
                    } else {
                        if (Telephony.Sms.getDefaultSmsPackage(this) != packageName) {
                            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                            }
                            roleLauncher.launch(intent)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    if (!permissionsGranted) {
                        val permissions = mutableListOf(
                            Manifest.permission.READ_SMS,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.SEND_SMS
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        launcher.launch(permissions.toTypedArray())
                    } else {
                        checkDefaultSmsRole()
                    }
                }

                LaunchedEffect(permissionsGranted) {
                    if (permissionsGranted) {
                        checkDefaultSmsRole()
                    }
                }

                if (permissionsGranted) {
                    val navController = rememberNavController()

                    LaunchedEffect(intent) {
                        val threadId = intent.getLongExtra("threadId", -1L)
                        if (threadId != -1L) {
                            navController.navigate("detail/$threadId")
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "list",
                        enterTransition = { slideInHorizontally { it } },
                        exitTransition = { slideOutHorizontally { -it } },
                        popEnterTransition = { slideInHorizontally { -it } },
                        popExitTransition = { slideOutHorizontally { it } }
                    ) {
                        composable("list") {
                            ConversationListScreen(
                                viewModel = viewModel,
                                onConversationClick = { threadId ->
                                    navController.navigate("detail/$threadId")
                                },
                                onNewConversation = { phoneNumber ->
                                    // Get or create thread ID for the phone number
                                    val threadId = viewModel.getOrCreateThreadId(phoneNumber)
                                    if (threadId != null) {
                                        navController.navigate("detail/$threadId")
                                    }
                                }
                            )
                        }
                        composable("detail/{threadId}") { backStackEntry ->
                            val threadId = backStackEntry.arguments?.getString("threadId")?.toLongOrNull() ?: -1L
                            ConversationDetailScreen(
                                threadId = threadId,
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        // Show placeholder or request button
                    }
                }
            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Notifications"
            val descriptionText = "Notifications for incoming SMS messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("sms_notifications", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}