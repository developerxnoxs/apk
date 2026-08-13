package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.Chat
import com.example.data.models.User
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramGreen
import com.example.ui.viewmodels.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onChatCreated: (Chat) -> Unit,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.allContacts.collectAsState()
    var showAddContactDialog by remember { mutableStateOf(false) }

    var newPhone by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kontak Saya 👥", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddContactDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Tambah Kontak", tint = TelegramBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddContactDialog = true },
                containerColor = TelegramBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAddContactDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = TelegramBlue)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Tambah Kontak Baru", fontWeight = FontWeight.SemiBold, color = TelegramBlue, fontSize = 16.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
            }

            items(contacts, key = { it.id }) { contact ->
                ContactItemRow(
                    contact = contact,
                    onClick = {
                        viewModel.startChatWithContact(contact, isSecret = true) { chat ->
                            onChatCreated(chat)
                        }
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(start = 80.dp)
                )
            }
        }
    }

    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newPhone.isNotBlank()) {
                            showAddContactDialog = false
                            val newContact = User(
                                id = "contact_${System.currentTimeMillis()}",
                                phoneNumber = newPhone,
                                displayName = newName,
                                bio = "Teman Baru di Telegram E2EE",
                                avatarUrl = "https://picsum.photos/seed/${newName.hashCode()}/200/200"
                            )
                            viewModel.startChatWithContact(newContact, isSecret = true) { chat ->
                                onChatCreated(chat)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                ) {
                    Text("Mulai Chat E2EE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("Batal")
                }
            },
            title = { Text("Tambah Kontak Baru") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nama Kontak") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Nomor Telepon (+62...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}

@Composable
fun ContactItemRow(
    contact: User,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("contact_item_${contact.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(TelegramBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (contact.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = contact.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = contact.displayName.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = TelegramBlue
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(contact.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(contact.phoneNumber, fontSize = 13.sp, color = Color.Gray)
        }

        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = TelegramGreen),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Rahasia", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
