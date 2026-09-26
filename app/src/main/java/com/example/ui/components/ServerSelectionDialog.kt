package com.example.ui.components

import com.example.ui.components.tvFocusRing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.AppPrefs
import com.example.data.repository.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun ServerSelectionDialog(
    prefs: AppPrefs,
    isFirstLaunch: Boolean = false,
    onDismiss: () -> Unit,
    onServerSelected: (ServerConfig.ServerType) -> Unit
) {
    val currentServerType by ServerConfig.currentServer.collectAsState()
    val customUrlVal by ServerConfig.customBaseUrl.collectAsState()
    val apiKeyVal by ServerConfig.movieBoxApiKey.collectAsState()

    var selectedType by remember { mutableStateOf(currentServerType) }
    var customUrlInput by remember { mutableStateOf(customUrlVal) }
    var apiKeyInput by remember { mutableStateOf(apiKeyVal) }

    val coroutineScope = rememberCoroutineScope()
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testResultSuccess by remember { mutableStateOf<Boolean?>(null) }

    fun testServer(url: String, key: String) {
        coroutineScope.launch {
            isTestingConnection = true
            testResultText = null
            testResultSuccess = null
            val startTime = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()
                    val targetUrl = "${url.trim().removeSuffix("/")}/mb/home"
                    val reqBuilder = Request.Builder().url(targetUrl)
                    if (key.isNotBlank()) {
                        reqBuilder.header("X-API-Key", key.trim())
                    }
                    val resp = client.newCall(reqBuilder.build()).execute()
                    val code = resp.code
                    resp.close()
                    val latency = System.currentTimeMillis() - startTime
                    if (code in 200..299) {
                        Pair(true, "Connected successfully (${latency}ms)")
                    } else {
                        Pair(false, "Server responded with HTTP $code")
                    }
                } catch (e: Exception) {
                    Pair(false, "Connection error: ${e.localizedMessage ?: "Timeout"}")
                }
            }
            isTestingConnection = false
            testResultSuccess = result.first
            testResultText = result.second
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isFirstLaunch) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isFirstLaunch,
            dismissOnClickOutside = !isFirstLaunch,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .then(if (LocalIsTv.current) Modifier.width(560.dp) else Modifier.fillMaxWidth(0.92f))
                .wrapContentHeight()
                .padding(vertical = 24.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(28.dp)),
            color = Color(0xFF14141B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFE50914), Color(0xFFB81D24))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = if (isFirstLaunch) "Select Streaming Server" else "Server Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = if (isFirstLaunch)
                        "Select your preferred streaming server. You can change this anytime in Profile & Settings."
                    else
                        "Switch streaming sources or configure your custom API endpoint.",
                    color = Color(0xFFAAAAAF),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(20.dp))

                // Option 1: Primary Server
                ServerOptionCard(
                    modifier = Modifier.tvAutoFocus(),
                    title = "Primary Server (Default)",
                    badge = "Admin Uploads",
                    badgeColor = Color(0xFFE50914),
                    description = "Direct movies and series uploaded to your cloud database via the admin panel.",
                    icon = Icons.Default.CloudUpload,
                    isSelected = selectedType == ServerConfig.ServerType.MERA_SERVER,
                    onClick = {
                        selectedType = ServerConfig.ServerType.MERA_SERVER
                        testResultText = null
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Option 2: MovieBox Server (ElitePlex)
                ServerOptionCard(
                    title = "MovieBox Server",
                    badge = "ElitePlex API",
                    badgeColor = Color(0xFF2E7D32),
                    description = "Global MovieBox catalog with HD movies, web series, Asian dramas, and fast streaming.",
                    icon = Icons.Default.MovieFilter,
                    isSelected = selectedType == ServerConfig.ServerType.MOVIEBOX,
                    onClick = {
                        selectedType = ServerConfig.ServerType.MOVIEBOX
                        testResultText = null
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Option 3: Custom Server
                ServerOptionCard(
                    title = "Custom Server",
                    badge = "Advanced",
                    badgeColor = Color(0xFF1976D2),
                    description = "Connect your self-hosted server or an alternate API mirror URL.",
                    icon = Icons.Default.Tune,
                    isSelected = selectedType == ServerConfig.ServerType.CUSTOM,
                    onClick = {
                        selectedType = ServerConfig.ServerType.CUSTOM
                        testResultText = null
                    }
                )

                // Custom settings fields when CUSTOM or MOVIEBOX is selected
                AnimatedVisibility(visible = selectedType == ServerConfig.ServerType.CUSTOM || selectedType == ServerConfig.ServerType.MOVIEBOX) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1B1B26))
                            .border(1.dp, Color(0xFF2C2C3E), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = if (selectedType == ServerConfig.ServerType.CUSTOM) "Custom Endpoint" else "MovieBox API Details",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(8.dp))

                        if (selectedType == ServerConfig.ServerType.CUSTOM) {
                            OutlinedTextField(
                                value = customUrlInput,
                                onValueChange = { customUrlInput = it },
                                label = { Text("Base URL", fontSize = 12.sp) },
                                placeholder = { Text("https://eliteplex-api.vercel.app") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFE50914),
                                    unfocusedBorderColor = Color(0xFF38384A),
                                    focusedLabelColor = Color(0xFFE50914),
                                    unfocusedLabelColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("API Key (Optional)", fontSize = 12.sp) },
                            placeholder = { Text("Leave blank if not required") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE50914),
                                unfocusedBorderColor = Color(0xFF38384A),
                                focusedLabelColor = Color(0xFFE50914),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                modifier = Modifier.tvFocusRing(RoundedCornerShape(20.dp)),
                                onClick = {
                                    val testUrl = if (selectedType == ServerConfig.ServerType.CUSTOM) customUrlInput else ServerConfig.DEFAULT_MOVIEBOX_URL
                                    testServer(testUrl, apiKeyInput)
                                },
                                enabled = !isTestingConnection,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.linearGradient(listOf(Color(0xFF555566), Color(0xFF777788)))
                                )
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(6.dp))
                                } else {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("Test Connection", fontSize = 12.sp)
                            }

                            if (selectedType == ServerConfig.ServerType.MOVIEBOX) {
                                Text(
                                    text = "http://eliteplex-api.vercel.app",
                                    color = Color(0xFF888899),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        testResultText?.let { msg ->
                            Spacer(Modifier.height(10.dp))
                            val isSuccess = testResultSuccess == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSuccess) Color(0x224CAF50) else Color(0x22F44336))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = msg,
                                    color = if (isSuccess) Color(0xFF81C784) else Color(0xFFE57373),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Button(
                    onClick = {
                        ServerConfig.setServer(
                            server = selectedType,
                            customUrl = if (selectedType == ServerConfig.ServerType.CUSTOM) customUrlInput else null,
                            apiKey = apiKeyInput,
                            prefs = prefs
                        )
                        onServerSelected(selectedType)
                        onDismiss()
                    },
                    modifier = Modifier.tvFocusRing(RoundedCornerShape(25.dp))
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE50914),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isFirstLaunch) "Confirm & Start Streaming" else "Apply Server Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (!isFirstLaunch) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.tvFocusRing(RoundedCornerShape(50.dp)).fillMaxWidth()
                    ) {
                        Text("Cancel", color = Color(0xFFAAAAAF), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerOptionCard(
    title: String,
    badge: String,
    badgeColor: Color,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFE50914) else Color(0xFF262635),
        animationSpec = tween(200),
        label = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF22151B) else Color(0xFF181822),
        animationSpec = tween(200),
        label = "bg"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(18.dp))
            .tvFocusRing(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF3B1015) else Color(0xFF242434)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFFE50914) else Color(0xFFA0A0B0),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = description,
                    color = Color(0xFF9090A0),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFFE50914),
                    unselectedColor = Color(0xFF48485C)
                )
            )
        }
    }
}
