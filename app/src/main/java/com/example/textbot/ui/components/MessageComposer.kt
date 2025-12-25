package com.example.textbot.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.example.textbot.R
import com.example.textbot.data.model.Attachment
import com.google.android.gms.location.LocationServices
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageComposer(
    onSendMessage: (String, List<Attachment>) -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<Attachment>() }
    val context = LocalContext.current
    
    // Pickers logic
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { attachments.add(Attachment(it.toString(), "image/*")) }
    }

    // Camera Capture logic
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedImageUri?.let { attachments.add(Attachment(it.toString(), "image/jpeg")) }
        }
    }

    // Document picker
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { attachments.add(Attachment(it.toString(), context.contentResolver.getType(it) ?: "*/*")) }
    }

    // Contact picker
    val contactPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let { attachments.add(Attachment(it.toString(), ContactsContract.Contacts.CONTENT_ITEM_TYPE)) }
    }

    // Permission handle for camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = createTempImageFile(context)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            capturedImageUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Permission handle for location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocation(context) { locationStr ->
                messageText += "\n${context.getString(R.string.prefix_my_location)}$locationStr"
            }
        }
    }

    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }

    // Permission handle for calendar
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        showEventDialog = true
    }

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column {
            // Attachment Previews
            AnimatedVisibility(visible = attachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachments) { attachment ->
                        AttachmentPreview(attachment) {
                            attachments.remove(attachment)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                    Icon(
                        if (showAttachmentMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(R.string.content_description_attach),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text(stringResource(R.string.placeholder_type_message)) },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    ),
                    maxLines = 4
                )

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank() || attachments.isNotEmpty()) {
                            onSendMessage(messageText, attachments.toList())
                            messageText = ""
                            attachments.clear()
                            showAttachmentMenu = false
                        }
                    },
                    enabled = messageText.isNotBlank() || attachments.isNotEmpty(),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.action_send)
                    )
                }
            }

            // Attachment Options Menu
            AnimatedVisibility(visible = showAttachmentMenu) {
                AttachmentOptionsMenu(
                    onOptionClick = { option ->
                        showAttachmentMenu = false
                        when (option) {
                            AttachmentOption.IMAGE -> imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            AttachmentOption.CAMERA -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            AttachmentOption.FILE -> documentPicker.launch(arrayOf("*/*"))
                            AttachmentOption.LOCATION -> locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                            AttachmentOption.CONTACT -> contactPicker.launch(null)
                            AttachmentOption.EVENT -> {
                                calendarPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                                )
                            }
                        }
                    }
                )
            }

            if (showEventDialog) {
                EventCreationDialog(
                    onDismiss = { showEventDialog = false },
                    onEventCreated = { title, location, dateStr, icsUri ->
                        messageText += "\n${context.getString(R.string.prefix_event_label)}$title\n📍 $location\n⏰ $dateStr"
                        if (icsUri != null) {
                            attachments.add(Attachment(icsUri.toString(), "text/calendar"))
                        }
                        showEventDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun EventCreationDialog(
    onDismiss: () -> Unit,
    onEventCreated: (String, String, String, Uri?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    val calendar = Calendar.getInstance()
    val dateTimeFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()) }
    var dateStr by remember { mutableStateOf(dateTimeFormatter.format(calendar.time)) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_create_event)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.label_event_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.label_event_location)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text(stringResource(R.string.label_event_date)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        try {
                            val cr = context.contentResolver
                            val values = android.content.ContentValues().apply {
                                put(android.provider.CalendarContract.Events.DTSTART, parseDate(dateStr))
                                put(android.provider.CalendarContract.Events.DTEND, parseDate(dateStr) + 3600000)
                                put(android.provider.CalendarContract.Events.TITLE, title)
                                put(android.provider.CalendarContract.Events.EVENT_LOCATION, location)
                                put(android.provider.CalendarContract.Events.CALENDAR_ID, 1)
                                put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                            }
                            try {
                                cr.insert(android.provider.CalendarContract.Events.CONTENT_URI, values)
                            } catch (e: SecurityException) { }
                            
                            val icsUri = createIcsFile(context, title, location, dateStr)
                            onEventCreated(title, location, dateStr, icsUri)
                        } catch (e: Exception) {
                            android.util.Log.e("EventDialog", "Error creating event", e)
                            onEventCreated(title, location, dateStr, null)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.action_save_and_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun parseDate(dateStr: String): Long {
    return try {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).parse(dateStr)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}

private fun createIcsFile(context: Context, title: String, location: String, dateStr: String): Uri? {
    return try {
        val startDate = parseDate(dateStr)
        val endDate = startDate + 3600000
        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        
        val icsContent = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//TextBot//Event//EN
            BEGIN:VEVENT
            UID:${UUID.randomUUID()}
            DTSTAMP:${dateFormat.format(Date())}
            DTSTART:${dateFormat.format(Date(startDate))}
            DTEND:${dateFormat.format(Date(endDate))}
            SUMMARY:$title
            LOCATION:$location
            DESCRIPTION:Generated by TextBot
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val fileName = "event_${System.currentTimeMillis()}.ics"
        val file = File(context.cacheDir, fileName)
        file.writeText(icsContent)
        
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        android.util.Log.e("IcsGeneration", "Error generating ICS", e)
        null
    }
}

@Composable
fun AttachmentPreview(attachment: Attachment, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        if (attachment.contentType.startsWith("image/")) {
            AsyncImage(
                model = attachment.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Description, contentDescription = null)
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_description_remove), tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun AttachmentOptionsMenu(onOptionClick: (AttachmentOption) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        AttachmentIcon(Icons.Default.Image, stringResource(R.string.attachment_image), AttachmentOption.IMAGE, onOptionClick)
        AttachmentIcon(Icons.Default.PhotoCamera, stringResource(R.string.attachment_camera), AttachmentOption.CAMERA, onOptionClick)
        AttachmentIcon(Icons.Default.AttachFile, stringResource(R.string.attachment_file), AttachmentOption.FILE, onOptionClick)
        AttachmentIcon(Icons.Default.LocationOn, stringResource(R.string.attachment_location), AttachmentOption.LOCATION, onOptionClick)
        AttachmentIcon(Icons.Default.Person, stringResource(R.string.attachment_contact), AttachmentOption.CONTACT, onOptionClick)
        AttachmentIcon(Icons.Default.Event, stringResource(R.string.attachment_event), AttachmentOption.EVENT, onOptionClick)
    }
}

@Composable
fun AttachmentIcon(icon: ImageVector, label: String, option: AttachmentOption, onClick: (AttachmentOption) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = { onClick(option) },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .size(48.dp)
            ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

enum class AttachmentOption {
    IMAGE, CAMERA, FILE, LOCATION, CONTACT, EVENT
}

private fun createTempImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
    return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
}

@SuppressLint("MissingPermission")
private fun fetchLocation(context: Context, onLocationFound: (String) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            onLocationFound("https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}")
        } else {
            onLocationFound("Localisation indisponible")
        }
    }
}
