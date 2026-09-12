package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.api.AddressItem
import com.example.api.SchoolItem
import com.example.profile.EditProfileViewModel
import com.example.utils.AvatarUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: EditProfileViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showAvatarPickerSheet by remember { mutableStateOf(false) }

    // Android Photo Picker Launcher (Google Play Policy Compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onAvatarSelected(uri.toString())
        }
    }

    // DatePicker Dialog
    val calendar = remember { Calendar.getInstance() }
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                viewModel.onDobSelected(year, month, dayOfMonth)
            },
            calendar.get(Calendar.YEAR) - 16,
            0,
            1
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "প্রোফাইল এডিট",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveProfile(onSuccess = onBack)
                        },
                        enabled = !uiState.isSaving && !uiState.isLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                text = "পরিবর্তন সংরক্ষণ করুন",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Success / Error Banner
                if (uiState.errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                if (uiState.successMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.successMessage ?: "",
                            color = Color(0xFF2E7D32),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // Avatar & Change Photo Section
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .clickable { showAvatarPickerSheet = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarReq = remember(uiState.avatarUrl, context) {
                                    AvatarUtils.buildImageRequest(context, uiState.avatarUrl)
                                }
                                if (avatarReq != null) {
                                    SubcomposeAsyncImage(
                                        model = avatarReq,
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        error = {
                                            Text(
                                                text = uiState.firstName.firstOrNull()?.toString()?.uppercase() ?: "S",
                                                color = Color.White,
                                                fontSize = 36.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    )
                                } else {
                                    Text(
                                        text = uiState.firstName.firstOrNull()?.toString()?.uppercase() ?: "S",
                                        color = Color.White,
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Camera Edit Badge
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                                shadowElevation = 3.dp,
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Change Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Text Action
                        TextButton(
                            onClick = { showAvatarPickerSheet = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "প্রোফাইল ছবি পরিবর্তন করুন",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 1. ব্যক্তিগত তথ্য (Personal Info)
                FormSectionCard(
                    title = "ব্যক্তিগত তথ্য",
                    icon = Icons.Default.Person,
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    // First Name
                    OutlinedTextField(
                        value = uiState.firstName,
                        onValueChange = { viewModel.onFirstNameChange(it) },
                        label = { Text("পূর্ণ নাম") },
                        placeholder = { Text("তোমার নাম লিখো") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Phone Number (Read Only)
                    OutlinedTextField(
                        value = uiState.phone,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("ফোন নম্বর (অ্যাকাউন্ট)") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Gender Selector
                    Text(
                        text = "লিঙ্গ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val genders = listOf("Male" to "পুরুষ", "Female" to "মহিলা")
                        genders.forEach { (key, label) ->
                            val isSelected = uiState.gender.equals(key, ignoreCase = true)
                            ChoiceChipButton(
                                text = label,
                                isSelected = isSelected,
                                onClick = { viewModel.onGenderChange(key) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Date of Birth
                    Text(
                        text = "জন্মতারিখ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { datePickerDialog.show() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = uiState.dobDisplay.ifBlank { "জন্মতারিখ নির্বাচন করো" },
                                    fontSize = 14.sp,
                                    color = if (uiState.dobDisplay.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = "Pick Date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 2. অভিভাবকের তথ্য (Guardian Info)
                FormSectionCard(
                    title = "অভিভাবকের তথ্য",
                    icon = Icons.Default.FamilyRestroom,
                    iconTint = Color(0xFF0284C7)
                ) {
                    OutlinedTextField(
                        value = uiState.guardianName,
                        onValueChange = { viewModel.onGuardianNameChange(it) },
                        label = { Text("অভিভাবকের নাম") },
                        placeholder = { Text("মাতা / পিতার নাম লিখো") },
                        leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.guardianMobile,
                        onValueChange = { viewModel.onGuardianMobileChange(it) },
                        label = { Text("অভিভাবকের মোবাইল নম্বর") },
                        placeholder = { Text("০১XXXXXXXXX") },
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // 3. শিক্ষাপ্রতিষ্ঠান নির্বাচন (Educational Institution)
                FormSectionCard(
                    title = "শিক্ষাপ্রতিষ্ঠান",
                    icon = Icons.Default.School,
                    iconTint = Color(0xFF059669)
                ) {
                    // Current Class & Group Preview
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "শ্রেণি: ${uiState.userClassDisplay.ifBlank { uiState.userClassCode }}  |  বিভাগ: ${uiState.userGroup.ifBlank { "সাধারণ" }}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Shift Selector
                    Text(
                        text = "শিফট (Shift)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val shifts = listOf("Morning" to "মর্নিং", "Day" to "ডে", "NA" to "প্রযোজ্য নয়")
                        shifts.forEach { (key, label) ->
                            val isSelected = uiState.shift.equals(key, ignoreCase = true)
                            ChoiceChipButton(
                                text = label,
                                isSelected = isSelected,
                                onClick = { viewModel.onShiftChange(key) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Division Dropdown
                    DropdownSelectorField(
                        label = "বিভাগ (Division)",
                        selectedValue = uiState.selectedDivisionName.ifBlank { "বিভাগ নির্বাচন করো" },
                        items = uiState.divisions.map { it.display ?: "" },
                        onItemSelected = { selectedName ->
                            val item = uiState.divisions.find { it.display == selectedName }
                            if (item != null) viewModel.onSelectDivision(item)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // District Dropdown
                    DropdownSelectorField(
                        label = "জেলা (District)",
                        selectedValue = when {
                            uiState.isDistrictsLoading -> "জেলা লোড হচ্ছে..."
                            uiState.selectedDistrictName.isNotBlank() -> uiState.selectedDistrictName
                            else -> "জেলা নির্বাচন করো"
                        },
                        items = uiState.districts.map { it.display ?: "" },
                        enabled = uiState.districts.isNotEmpty(),
                        onItemSelected = { selectedName ->
                            val item = uiState.districts.find { it.display == selectedName }
                            if (item != null) viewModel.onSelectDistrict(item)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // School Name / Picker Button
                    Text(
                        text = "স্কুল / কলেজ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setSchoolSearchDialogVisible(true)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.selectedSchoolName.ifBlank { "স্কুল / কলেজ খুঁজুন ও নির্বাচন করুন" },
                                fontSize = 14.sp,
                                fontWeight = if (uiState.selectedSchoolName.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                                color = if (uiState.selectedSchoolName.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search School",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 4. বোর্ড পরীক্ষার তথ্য (Board Exam Info)
                FormSectionCard(
                    title = "বোর্ড পরীক্ষার তথ্য",
                    icon = Icons.Default.Assignment,
                    iconTint = Color(0xFFD97706)
                ) {
                    // SSC Board Name
                    DropdownSelectorField(
                        label = "এসএসসি বোর্ড (SSC Board)",
                        selectedValue = uiState.sscBoardName,
                        items = viewModel.educationBoards,
                        onItemSelected = { viewModel.onSscBoardChange(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // SSC Roll
                    OutlinedTextField(
                        value = uiState.sscRollNumber,
                        onValueChange = { viewModel.onSscRollChange(it) },
                        label = { Text("এসএসসি রোল নম্বর (SSC Roll)") },
                        placeholder = { Text("যেমন: 762180") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Board Reg Number
                    OutlinedTextField(
                        value = uiState.boardRegNumber,
                        onValueChange = { viewModel.onBoardRegChange(it) },
                        label = { Text("বোর্ড রেজিস্ট্রেশন নম্বর (Reg No)") },
                        placeholder = { Text("যেমন: 2213531789") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // For HSC / C11 / C12, show HSC Board & HSC Roll
                    val isHscOrHigher = uiState.userClassCode.uppercase() in listOf("C11", "C12", "HSC", "ADMISSION")
                    if (isHscOrHigher) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(14.dp))

                        DropdownSelectorField(
                            label = "এইচএসসি বোর্ড (HSC Board)",
                            selectedValue = uiState.hscBoardName,
                            items = viewModel.educationBoards,
                            onItemSelected = { viewModel.onHscBoardChange(it) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = uiState.hscRollNumber,
                            onValueChange = { viewModel.onHscRollChange(it) },
                            label = { Text("এইচএসসি রোল নম্বর (HSC Roll - যদি থাকে)") },
                            placeholder = { Text("যেমন: 880000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }

    // School Search Dialog
    if (uiState.showSchoolSearchDialog) {
        SchoolSearchDialog(
            searchResults = uiState.schoolSearchResults,
            isLoading = uiState.isSchoolSearching,
            onSearchQuery = { query -> viewModel.searchSchools(query) },
            onSelectSchool = { school -> viewModel.onSelectSchool(school) },
            onDismiss = { viewModel.setSchoolSearchDialogVisible(false) }
        )
    }

    // Avatar Selection Bottom Sheet
    if (showAvatarPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarPickerSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "প্রোফাইল ছবি পরিবর্তন",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 1. Pick from Gallery
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showAvatarPickerSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "গ্যালারি থেকে ছবি বেছে নিন",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "তোমার ডিভাইস থেকে নতুন ছবি আপলোড করো",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 2. Preset Avatars
                Text(
                    text = "অথবা একটি অবতার বেছে নিন",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val presetAvatars = listOf(
                    "https://api.dicebear.com/7.x/adventurer/png?seed=Felix",
                    "https://api.dicebear.com/7.x/adventurer/png?seed=Aneka",
                    "https://api.dicebear.com/7.x/adventurer/png?seed=Sam",
                    "https://api.dicebear.com/7.x/adventurer/png?seed=Milo",
                    "https://api.dicebear.com/7.x/adventurer/png?seed=Zoe",
                    "https://api.dicebear.com/7.x/bottts/png?seed=StudentRobot"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetAvatars.forEach { avatarUrl ->
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(
                                2.dp,
                                if (uiState.avatarUrl == avatarUrl) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    viewModel.onAvatarSelected(avatarUrl)
                                    showAvatarPickerSheet = false
                                }
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Preset Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // 3. Remove photo (if has custom avatar)
                if (!uiState.avatarUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            viewModel.onAvatarRemoved()
                            showAvatarPickerSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "বর্তমান ছবি মুছে ফেলুন",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun ChoiceChipButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelectorField(
    label: String,
    selectedValue: String,
    items: List<String>,
    onItemSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                items.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 14.sp) },
                        onClick = {
                            onItemSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SchoolSearchDialog(
    searchResults: List<SchoolItem>,
    isLoading: Boolean,
    onSearchQuery: (String) -> Unit,
    onSelectSchool: (SchoolItem) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "স্কুল / কলেজ খুঁজুন",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearchQuery(it)
                    },
                    placeholder = { Text("প্রতিষ্ঠানের নাম লিখুন...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোনো প্রতিষ্ঠান পাওয়া যায়নি",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults, key = { it.id ?: UUID.randomUUID().toString() }) { school ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectSchool(school) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = school.name ?: "",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
