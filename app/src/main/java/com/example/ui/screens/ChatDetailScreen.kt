package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.crypto.CryptoUtils
import com.example.data.models.Chat
import com.example.data.models.Message
import com.example.data.models.MediaType
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TelegramGreen
import com.example.ui.theme.TelegramLightBubbleOut
import com.example.ui.viewmodels.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeChat by viewModel.activeChat.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val typingUser by viewModel.activeChatTypingUser.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showFingerprintDialog by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecordSeconds by remember { mutableStateOf(0) }
    var selectedMessageForOptions by remember { mutableStateOf<Message?>(null) }
    var messageToEditInDialog by remember { mutableStateOf<Message?>(null) }
    var editDialogTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    var selectedImageForModal by remember { mutableStateOf<String?>(null) }

    val bottomBarFocusRequester = remember { FocusRequester() }
    val dialogFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // When an editing message is set, fill input text and request focus
    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            inputText = editingMessage?.cipherText ?: ""
            delay(200)
            try {
                bottomBarFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    // Broadcast user typing when typing in textfield
    LaunchedEffect(inputText) {
        viewModel.setUserTyping(inputText.isNotBlank())
    }

    // Auto-scroll to latest message or typing indicator
    LaunchedEffect(messages.size, typingUser) {
        val totalCount = messages.size + if (typingUser != null) 1 else 0
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount)
        }
    }

    // Voice recording timer simulation
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            voiceRecordSeconds = 0
            while (isRecordingVoice) {
                kotlinx.coroutines.delay(1000)
                voiceRecordSeconds++
            }
        }
    }

    val currentChat = activeChat ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showFingerprintDialog = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TelegramBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentChat.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = currentChat.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = currentChat.title.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = TelegramBlue
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentChat.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (currentChat.isSecret) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Secret",
                                        tint = TelegramGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            if (typingUser != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = TelegramBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AnimatedTypingText(text = "sedang mengetik")
                                }
                            } else {
                                Text(
                                    text = "online • 🔐 E2EE Secured",
                                    fontSize = 12.sp,
                                    color = TelegramGreen
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showFingerprintDialog = true }, modifier = Modifier.testTag("verify_key_button")) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Verifikasi Kunci E2EE", tint = TelegramBlue)
                    }
                    IconButton(onClick = {
                        Toast.makeText(context, "📞 Panggilan Suara Terenkripsi End-to-End E2EE", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Panggil", tint = TelegramBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                if (editingMessage != null) {
                    // Telegram Edit Message Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Pesan",
                            tint = TelegramBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mengedit Pesan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TelegramBlue
                            )
                            Text(
                                text = editingMessage?.cipherText ?: "",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.setEditingMessage(null)
                                inputText = ""
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Batal Edit",
                                tint = Color.Gray
                            )
                        }
                    }
                } else if (isRecordingVoice) {
                    // Voice Recording Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Merekam Suara: 00:${voiceRecordSeconds.toString().padStart(2, '0')}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Row {
                            TextButton(onClick = { isRecordingVoice = false }) {
                                Text("Batal", color = MaterialTheme.colorScheme.error)
                            }
                            Button(
                                onClick = {
                                    isRecordingVoice = false
                                    viewModel.sendMessage(
                                        text = "🎤 Pesan suara ($voiceRecordSeconds detik)",
                                        mediaType = MediaType.VOICE,
                                        mediaUrl = "https://example.com/audio.mp3",
                                        mediaName = "VoiceNote_${System.currentTimeMillis()}.mp3",
                                        mediaSize = "0:${voiceRecordSeconds.toString().padStart(2, '0')}"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                            ) {
                                Text("Kirim")
                            }
                        }
                    }
                } else {
                    // Standard Message Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (editingMessage == null) {
                            IconButton(onClick = { showAttachmentSheet = true }, modifier = Modifier.testTag("attach_button")) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Lampiran", tint = TelegramBlue)
                            }
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ketik pesan terenkripsi...") },
                            singleLine = false,
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TelegramBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(bottomBarFocusRequester)
                                .testTag("message_input")
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        if (editingMessage != null) {
                            IconButton(
                                onClick = {
                                    val text = inputText
                                    inputText = ""
                                    viewModel.saveEditedMessage(editingMessage?.id ?: "", text)
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(TelegramGreen)
                                    .testTag("confirm_edit_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Simpan Perubahan", tint = Color.White)
                            }
                        } else if (inputText.isBlank()) {
                            IconButton(
                                onClick = { isRecordingVoice = true },
                                modifier = Modifier.testTag("record_voice_button")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Rekam Suara", tint = TelegramBlue)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val textToSend = inputText
                                    inputText = ""
                                    viewModel.sendMessage(textToSend)
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(TelegramBlue)
                                    .testTag("send_message_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim", tint = Color.White)
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                item {
                    // E2EE Security Top Banner
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = TelegramGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Obrolan Rahasia Terenkripsi End-to-End",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "• Menggunakan enkripsi simetris AES-256-GCM\n• Pesan langsung didekripsi di perangkat Anda\n• Kunci fingerprint: ${CryptoUtils.generateEmojiFingerprint(currentChat.secretKey)}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    val isMe = msg.senderId == currentUser?.id
                    MessageBubble(
                        message = msg,
                        isMe = isMe,
                        onImageClick = { url -> selectedImageForModal = url },
                        onLongClick = {
                            selectedMessageForOptions = msg
                        }
                    )
                }

                // Telegram Typing Bubble in message list
                if (typingUser != null) {
                    item(key = "typing_indicator") {
                        TypingBubble(
                            senderName = typingUser ?: "Kontak",
                            avatarUrl = currentChat.avatarUrl
                        )
                    }
                }
            }
        }
    }

    // Message Long-Press Options Bottom Sheet (Edit / Copy)
    if (selectedMessageForOptions != null) {
        val targetMsg = selectedMessageForOptions!!
        val currentUserId = currentUser?.id ?: ""
        val isMyMsg = targetMsg.senderId == currentUserId ||
                      targetMsg.senderId == "user_me" ||
                      targetMsg.senderId == "me" ||
                      targetMsg.senderName.contains("Saya", ignoreCase = true) ||
                      targetMsg.senderName == (currentUser?.displayName ?: "")

        ModalBottomSheet(
            onDismissRequest = { selectedMessageForOptions = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "Opsi Pesan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TelegramBlue,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (isMyMsg && targetMsg.mediaType == MediaType.TEXT) {
                    ListItem(
                        headlineContent = { Text("Edit Pesan ✏️", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Ubah teks pesan terenkripsi ini") },
                        leadingContent = {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = TelegramBlue)
                        },
                        modifier = Modifier
                            .clickable {
                                val msgToEdit = targetMsg
                                selectedMessageForOptions = null
                                viewModel.setEditingMessage(msgToEdit)
                                messageToEditInDialog = msgToEdit
                                editDialogTextFieldValue = TextFieldValue(
                                    text = msgToEdit.cipherText,
                                    selection = TextRange(msgToEdit.cipherText.length)
                                )
                            }
                            .testTag("option_edit_message")
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }

                ListItem(
                    headlineContent = { Text("Salin Teks Pesan 📋", fontWeight = FontWeight.Medium) },
                    leadingContent = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TelegramBlue)
                    },
                    modifier = Modifier
                        .clickable {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Telegram Message", targetMsg.cipherText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Pesan disalin ke clipboard", Toast.LENGTH_SHORT).show()
                            selectedMessageForOptions = null
                        }
                        .testTag("option_copy_message")
                )
            }
        }
    }

    // Dedicated Edit Message Dialog with Auto-Keyboard Focus
    if (messageToEditInDialog != null) {
        val editing = messageToEditInDialog!!

        LaunchedEffect(editing.id) {
            delay(250)
            try {
                dialogFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }

        AlertDialog(
            onDismissRequest = {
                messageToEditInDialog = null
            },
            icon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = TelegramBlue,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Edit Pesan Terenkripsi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Edit pesan Anda. Setelah disimpan, teks akan langsung dienkripsi ulang (E2EE AES-256) dan ditandai (diedit).",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = editDialogTextFieldValue,
                        onValueChange = { editDialogTextFieldValue = it },
                        placeholder = { Text("Ketik pesan baru...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(dialogFocusRequester)
                            .testTag("dialog_edit_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TelegramBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        minLines = 2,
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newText = editDialogTextFieldValue.text.trim()
                        if (newText.isNotBlank()) {
                            viewModel.saveEditedMessage(editing.id, newText)
                            Toast.makeText(context, "Pesan berhasil diperbarui! ✏️", Toast.LENGTH_SHORT).show()
                        }
                        messageToEditInDialog = null
                        viewModel.setEditingMessage(null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                    modifier = Modifier.testTag("dialog_save_edit_button")
                ) {
                    Text("Simpan Perubahan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        messageToEditInDialog = null
                        viewModel.setEditingMessage(null)
                    }
                ) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }

    // Attachment Sheet
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Kirim Lampiran Terenkripsi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachmentOptionItem(
                        icon = Icons.Default.Image,
                        label = "Foto / Galeri",
                        color = Color(0xFF0088CC),
                        onClick = {
                            showAttachmentSheet = false
                            viewModel.sendMessage(
                                text = "📷 Mengirim foto terenkripsi",
                                mediaType = MediaType.IMAGE,
                                mediaUrl = "https://picsum.photos/seed/photo_${System.currentTimeMillis()}/600/400",
                                mediaName = "Photo.jpg",
                                mediaSize = "1.2 MB"
                            )
                        }
                    )

                    AttachmentOptionItem(
                        icon = Icons.Default.Mic,
                        label = "Pesan Suara",
                        color = Color(0xFF42C063),
                        onClick = {
                            showAttachmentSheet = false
                            isRecordingVoice = true
                        }
                    )

                    AttachmentOptionItem(
                        icon = Icons.Default.InsertDriveFile,
                        label = "Dokumen",
                        color = Color(0xFFE5695C),
                        onClick = {
                            showAttachmentSheet = false
                            viewModel.sendMessage(
                                text = "📄 Mengirim dokumen PDF",
                                mediaType = MediaType.FILE,
                                mediaUrl = "https://example.com/document.pdf",
                                mediaName = "Laporan_Keuangan_E2EE.pdf",
                                mediaSize = "2.4 MB"
                            )
                        }
                    )

                    AttachmentOptionItem(
                        icon = Icons.Default.LocationOn,
                        label = "Lokasi",
                        color = Color(0xFFF1B000),
                        onClick = {
                            showAttachmentSheet = false
                            viewModel.sendMessage(
                                text = "📍 Lokasi Terkini: Lat -6.2088, Long 106.8456 (Jakarta)",
                                mediaType = MediaType.LOCATION,
                                mediaUrl = "",
                                mediaName = "Jakarta, Indonesia",
                                mediaSize = "GPS Fixed"
                            )
                        }
                    )
                }
            }
        }
    }

    // Fingerprint Key Modal
    if (showFingerprintDialog) {
        KeyFingerprintDialog(
            chat = currentChat,
            onDismiss = { showFingerprintDialog = false }
        )
    }

    // Fullscreen Image Modal Preview
    if (selectedImageForModal != null) {
        AlertDialog(
            onDismissRequest = { selectedImageForModal = null },
            confirmButton = {
                TextButton(onClick = { selectedImageForModal = null }) {
                    Text("Tutup", color = TelegramBlue)
                }
            },
            title = { Text("🔍 Foto Terenkripsi E2EE") },
            text = {
                AsyncImage(
                    model = selectedImageForModal,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    onImageClick: (String) -> Unit,
    onReactionClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val timeFormatted = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    val bubbleColor = if (isMe) {
        if (MaterialTheme.colorScheme.background == Color(0xFF0E1621)) Color(0xFF2B5278) else TelegramLightBubbleOut
    } else {
        MaterialTheme.colorScheme.surface
    }

    val align = if (isMe) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = align,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("message_bubble_${message.id}")
    ) {
        Box(contentAlignment = if (isMe) Alignment.BottomEnd else Alignment.BottomStart) {
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 16.dp
                        )
                    )
                    .combinedClickable(
                        onClick = { /* normal tap */ },
                        onLongClick = { onLongClick() }
                    )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (!isMe) {
                        Text(
                            text = message.senderName,
                            fontWeight = FontWeight.Bold,
                            color = TelegramBlue,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    // Render based on media type
                    when (message.mediaType) {
                        MediaType.TEXT -> {
                            Text(
                                text = message.cipherText,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        MediaType.IMAGE -> {
                            if (message.mediaUrl.isNotBlank()) {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = "Foto Terenkripsi",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onImageClick(message.mediaUrl) }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = message.cipherText,
                                fontSize = 14.sp
                            )
                        }

                        MediaType.VOICE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(TelegramBlue)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Putar", tint = Color.White)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("•••••••••••••••", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = TelegramBlue)
                                    }
                                    Text("Pesan Suara (${message.mediaSize})", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        MediaType.FILE -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TelegramBlue.copy(alpha = 0.1f))
                                    .padding(8.dp)
                            ) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(message.mediaName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                    Text(message.mediaSize, fontSize = 11.sp, color = Color.Gray)
                                }
                                Icon(Icons.Default.FileDownload, contentDescription = null, tint = TelegramBlue)
                            }
                        }

                        MediaType.LOCATION -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(message.cipherText, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isEdited) {
                            Text(
                                text = "diedit",
                                fontSize = 10.sp,
                                fontStyle = FontStyle.Italic,
                                color = Color.Gray,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }

                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "E2EE",
                            tint = TelegramGreen,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = timeFormatted,
                            fontSize = 10.sp,
                            color = Color.Gray
                        )

                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = "Read",
                                tint = TelegramGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Attached reaction chip on bubble corner
            if (message.reaction.isNotBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .offset(y = 10.dp, x = if (isMe) (-6).dp else 6.dp)
                        .clickable { onLongClick() }
                        .testTag("reaction_badge_${message.id}")
                ) {
                    Text(
                        text = message.reaction,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (message.reaction.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun AttachmentOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun KeyFingerprintDialog(
    chat: Chat,
    onDismiss: () -> Unit
) {
    val fingerprint = remember(chat.secretKey) {
        CryptoUtils.generateEmojiFingerprint(chat.secretKey)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
            ) {
                Text("Tutup & Terverifikasi")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = TelegramGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kunci Fingerprint E2EE")
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Bandingkan visual simbol emoji berikut dengan perangkat ${chat.title}:",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(TelegramBlue.copy(alpha = 0.1f))
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = fingerprint,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TelegramBlue
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "🔐 Jika simbol emoji di atas cocok 100%, pesan Anda terjamin bebas dari penyadapan atau peretasan man-in-the-middle.",
                    fontSize = 12.sp,
                    color = TelegramGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
fun AnimatedTypingText(
    text: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots_transition")
    val dotCount by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots_count"
    )

    val dots = ".".repeat(dotCount)

    Text(
        text = "$text$dots",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = TelegramBlue,
        modifier = modifier
    )
}

@Composable
fun TypingBubble(
    senderName: String,
    avatarUrl: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_dots")

    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$senderName sedang mengetik",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = TelegramBlue,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 3 Bouncing Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = dot1Offset.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue)
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = dot2Offset.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue.copy(alpha = 0.8f))
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = dot3Offset.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}
