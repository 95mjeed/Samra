package com.samra.downloader.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps the design's Material Symbol names to Compose's filled icon set.
 * (Filled is used throughout; the FILL 0/1 distinction is approximated.)
 */
fun sym(name: String): ImageVector = when (name) {
    "graphic_eq" -> Icons.Rounded.GraphicEq
    "auto_stories" -> Icons.Rounded.AutoStories
    "menu_book" -> Icons.Rounded.MenuBook
    "podcasts" -> Icons.Rounded.Podcasts
    "local_library" -> Icons.Rounded.LocalLibrary
    "headphones" -> Icons.Rounded.Headphones
    "import_contacts" -> Icons.Rounded.ImportContacts
    "account_balance" -> Icons.Rounded.AccountBalance
    "cloud" -> Icons.Rounded.Cloud
    "record_voice_over" -> Icons.Rounded.RecordVoiceOver
    "person" -> Icons.Rounded.Person
    "cookie" -> Icons.Rounded.Cookie
    "search" -> Icons.Rounded.Search
    "grid_view" -> Icons.Rounded.GridView
    "view_list" -> Icons.Rounded.ViewList
    "view_agenda" -> Icons.Rounded.ViewAgenda
    "schedule" -> Icons.Rounded.Schedule
    "downloading" -> Icons.Rounded.Downloading
    "pause_circle" -> Icons.Rounded.PauseCircle
    "check_circle" -> Icons.Rounded.CheckCircle
    "check" -> Icons.Rounded.Check
    "radio_button_unchecked" -> Icons.Rounded.RadioButtonUnchecked
    "sticky_note_2" -> Icons.AutoMirrored.Rounded.StickyNote2
    "border_color" -> Icons.Rounded.BorderColor
    "draw" -> Icons.Rounded.Draw
    "undo" -> Icons.AutoMirrored.Rounded.Undo
    "content_copy" -> Icons.Rounded.ContentCopy
    "eraser" -> Icons.Rounded.AutoFixNormal
    "error" -> Icons.Rounded.Error
    "pause" -> Icons.Rounded.Pause
    "play_arrow" -> Icons.Rounded.PlayArrow
    "folder_open" -> Icons.Rounded.FolderOpen
    "refresh" -> Icons.Rounded.Refresh
    "delete" -> Icons.Rounded.Delete
    "close" -> Icons.Rounded.Close
    "content_paste" -> Icons.Rounded.ContentPaste
    "download" -> Icons.Rounded.Download
    "expand_less" -> Icons.Rounded.ExpandLess
    "expand_more" -> Icons.Rounded.ExpandMore
    "hub" -> Icons.Rounded.Hub
    "settings" -> Icons.Rounded.Settings
    "brightness_auto" -> Icons.Rounded.BrightnessAuto
    "dark_mode" -> Icons.Rounded.DarkMode
    "light_mode" -> Icons.Rounded.LightMode
    "verified_user" -> Icons.Rounded.VerifiedUser
    "mail" -> Icons.Rounded.Mail
    "lock" -> Icons.Rounded.Lock
    "visibility" -> Icons.Rounded.Visibility
    "visibility_off" -> Icons.Rounded.VisibilityOff
    "upload_file" -> Icons.Rounded.UploadFile
    "task_alt" -> Icons.Rounded.TaskAlt
    "help" -> Icons.Rounded.Help
    "login" -> Icons.Rounded.Login
    "link" -> Icons.Rounded.Link
    "bookmark" -> Icons.Rounded.Bookmark
    "bookmark_add" -> Icons.Rounded.BookmarkAdd
    "bookmark_border" -> Icons.Rounded.BookmarkBorder
    "bookmarks" -> Icons.Rounded.Bookmarks
    "replay_30" -> Icons.Rounded.Replay30
    "forward_30" -> Icons.Rounded.Forward30
    "skip_previous" -> Icons.Rounded.SkipPrevious
    "skip_next" -> Icons.Rounded.SkipNext
    "bedtime" -> Icons.Rounded.Bedtime
    "bedtime_off" -> Icons.Rounded.BedtimeOff
    "chrome_reader_mode" -> Icons.Rounded.ChromeReaderMode
    "play_circle" -> Icons.Rounded.PlayCircle
    "toc" -> Icons.Rounded.Toc
    "density_small" -> Icons.Rounded.DensitySmall
    "density_medium" -> Icons.Rounded.DensityMedium
    "density_large" -> Icons.Rounded.DensityLarge
    "local_cafe" -> Icons.Rounded.LocalCafe
    // Auto-mirrored so they flip with the layout direction (RTL ↔ LTR).
    "arrow_back" -> Icons.AutoMirrored.Rounded.ArrowBack
    "arrow_forward" -> Icons.AutoMirrored.Rounded.ArrowForward
    "chevron_left" -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
    "chevron_right" -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
    "add" -> Icons.Rounded.Add
    "cloud_download" -> Icons.Rounded.CloudDownload
    "business" -> Icons.Rounded.Business
    "calendar_today" -> Icons.Rounded.CalendarToday
    "translate" -> Icons.Rounded.Translate
    "search_off" -> Icons.Rounded.SearchOff
    "ios_share" -> Icons.Rounded.IosShare
    "share" -> Icons.Rounded.Share
    "select_all" -> Icons.Rounded.SelectAll
    "folder" -> Icons.Rounded.Folder
    "bluetooth" -> Icons.Rounded.Bluetooth
    "chat" -> Icons.Rounded.Chat
    "expand_more_player" -> Icons.Rounded.ExpandMore
    else -> Icons.Rounded.Circle
}
