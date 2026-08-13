package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.ui.viewmodels.AuthStep
import com.example.ui.viewmodels.ChatViewModel

object NavRoutes {
    const val AUTH = "auth"
    const val CHAT_LIST = "chat_list"
    const val CHAT_DETAIL = "chat_detail"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
}

@Composable
fun TelegramNavGraph(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val authStep by viewModel.authStep.collectAsState()

    val startDestination = if (authStep == AuthStep.LOGGED_IN) NavRoutes.CHAT_LIST else NavRoutes.AUTH

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(NavRoutes.AUTH) {
            AuthScreen(
                viewModel = viewModel
            )
        }

        composable(NavRoutes.CHAT_LIST) {
            ChatListScreen(
                viewModel = viewModel,
                onChatClick = { chat ->
                    viewModel.setActiveChat(chat)
                    navController.navigate(NavRoutes.CHAT_DETAIL)
                },
                onContactsClick = {
                    navController.navigate(NavRoutes.CONTACTS)
                },
                onSettingsClick = {
                    navController.navigate(NavRoutes.SETTINGS)
                }
            )
        }

        composable(NavRoutes.CHAT_DETAIL) {
            ChatDetailScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(NavRoutes.CONTACTS) {
            ContactsScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onChatCreated = { chat ->
                    viewModel.setActiveChat(chat)
                    navController.navigate(NavRoutes.CHAT_DETAIL) {
                        popUpTo(NavRoutes.CHAT_LIST)
                    }
                }
            )
        }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
