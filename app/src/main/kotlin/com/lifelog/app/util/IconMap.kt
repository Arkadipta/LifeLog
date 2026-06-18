package com.lifelog.app.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A named group of event icons shown together in the icon picker. Grouping the
 * larger library by activity keeps it browsable instead of an undifferentiated
 * grid — the section titles mirror the kinds of events users log.
 */
data class EventIconCategory(val title: String, val icons: List<Pair<String, ImageVector>>)

/**
 * The event icon library, grouped by category. This is the single source of
 * truth: [eventIconMap] is derived from it for name lookups, and the icon
 * picker renders it section by section.
 *
 * Every icon is `Icons.Rounded.*` (or its `AutoMirrored` variant for directional
 * glyphs) so the whole set shares one visual style with the rest of the app.
 * Keys are stable strings persisted on [com.lifelog.app.domain.model.EventType];
 * never rename a key once shipped or existing events lose their icon.
 */
val eventIconCategories: List<EventIconCategory> = listOf(
    EventIconCategory(
        "General", listOf(
            "star" to Icons.Rounded.Star,
            "favorite" to Icons.Rounded.Favorite,
            "mood" to Icons.Rounded.Mood,
            "flag" to Icons.Rounded.Flag,
            "lightbulb" to Icons.Rounded.Lightbulb,
            "bolt" to Icons.Rounded.Bolt,
            "whatshot" to Icons.Rounded.Whatshot,
            "check_circle" to Icons.Rounded.CheckCircle,
        )
    ),
    EventIconCategory(
        "Work & Career", listOf(
            "work" to Icons.Rounded.Work,
            "business_center" to Icons.Rounded.BusinessCenter,
            "badge" to Icons.Rounded.Badge,
            "meeting" to Icons.Rounded.Groups,
            "handshake" to Icons.Rounded.Handshake,
            "forum" to Icons.Rounded.Forum,
            "email" to Icons.Rounded.Email,
            "call" to Icons.Rounded.Call,
        )
    ),
    EventIconCategory(
        "Productivity", listOf(
            "task" to Icons.Rounded.TaskAlt,
            "checklist" to Icons.Rounded.Checklist,
            "event_note" to Icons.AutoMirrored.Rounded.EventNote,
            "schedule" to Icons.Rounded.Schedule,
            "timer" to Icons.Rounded.Timer,
            "calendar_month" to Icons.Rounded.CalendarMonth,
            "alarm" to Icons.Rounded.Alarm,
        )
    ),
    EventIconCategory(
        "Study & Learning", listOf(
            "school" to Icons.Rounded.School,
            "book" to Icons.Rounded.Book,
            "menu_book" to Icons.AutoMirrored.Rounded.MenuBook,
            "auto_stories" to Icons.Rounded.AutoStories,
            "science" to Icons.Rounded.Science,
            "edit_note" to Icons.Rounded.EditNote,
            "draw" to Icons.Rounded.Draw,
            "brush" to Icons.Rounded.Brush,
            "code" to Icons.Rounded.Code,
            "terminal" to Icons.Rounded.Terminal,
        )
    ),
    EventIconCategory(
        "Exercise & Fitness", listOf(
            "fitness_center" to Icons.Rounded.FitnessCenter,
            "directions_run" to Icons.AutoMirrored.Rounded.DirectionsRun,
            "directions_walk" to Icons.AutoMirrored.Rounded.DirectionsWalk,
            "hiking" to Icons.Rounded.Hiking,
            "directions_bike" to Icons.AutoMirrored.Rounded.DirectionsBike,
            "pool" to Icons.Rounded.Pool,
            "self_improvement" to Icons.Rounded.SelfImprovement,
        )
    ),
    EventIconCategory(
        "Sports", listOf(
            "sports_soccer" to Icons.Rounded.SportsSoccer,
            "sports_basketball" to Icons.Rounded.SportsBasketball,
            "sports_tennis" to Icons.Rounded.SportsTennis,
            "sports_esports" to Icons.Rounded.SportsEsports,
        )
    ),
    EventIconCategory(
        "Health & Medical", listOf(
            "medication" to Icons.Rounded.Medication,
            "monitor_heart" to Icons.Rounded.MonitorHeart,
            "health_and_safety" to Icons.Rounded.HealthAndSafety,
            "local_hospital" to Icons.Rounded.LocalHospital,
            "vaccines" to Icons.Rounded.Vaccines,
            "healing" to Icons.Rounded.Healing,
            "psychology" to Icons.Rounded.Psychology,
            "bedtime" to Icons.Rounded.Bedtime,
            "hotel" to Icons.Rounded.Hotel,
        )
    ),
    EventIconCategory(
        "Food & Dining", listOf(
            "restaurant" to Icons.Rounded.Restaurant,
            "fastfood" to Icons.Rounded.Fastfood,
            "lunch_dining" to Icons.Rounded.LunchDining,
            "local_pizza" to Icons.Rounded.LocalPizza,
            "cake" to Icons.Rounded.Cake,
            "coffee" to Icons.Rounded.Coffee,
            "local_cafe" to Icons.Rounded.LocalCafe,
            "emoji_food_beverage" to Icons.Rounded.EmojiFoodBeverage,
            "local_bar" to Icons.Rounded.LocalBar,
            "local_drink" to Icons.Rounded.LocalDrink,
        )
    ),
    EventIconCategory(
        "Travel & Transport", listOf(
            "flight" to Icons.Rounded.Flight,
            "luggage" to Icons.Rounded.Luggage,
            "travel_explore" to Icons.Rounded.TravelExplore,
            "map" to Icons.Rounded.Map,
            "public" to Icons.Rounded.Public,
            "beach_access" to Icons.Rounded.BeachAccess,
            "directions_car" to Icons.Rounded.DirectionsCar,
            "train" to Icons.Rounded.Train,
            "directions_bus" to Icons.Rounded.DirectionsBus,
        )
    ),
    EventIconCategory(
        "Shopping & Finance", listOf(
            "shopping_cart" to Icons.Rounded.ShoppingCart,
            "shopping_bag" to Icons.Rounded.ShoppingBag,
            "local_mall" to Icons.Rounded.LocalMall,
            "checkroom" to Icons.Rounded.Checkroom,
            "card_giftcard" to Icons.Rounded.CardGiftcard,
            "attach_money" to Icons.Rounded.AttachMoney,
            "savings" to Icons.Rounded.Savings,
            "account_balance" to Icons.Rounded.AccountBalance,
            "account_balance_wallet" to Icons.Rounded.AccountBalanceWallet,
            "credit_card" to Icons.Rounded.CreditCard,
            "payments" to Icons.Rounded.Payments,
            "trending_up" to Icons.AutoMirrored.Rounded.TrendingUp,
        )
    ),
    EventIconCategory(
        "Family & Social", listOf(
            "family" to Icons.Rounded.FamilyRestroom,
            "child_care" to Icons.Rounded.ChildCare,
            "people" to Icons.Rounded.People,
            "diversity" to Icons.Rounded.Diversity3,
            "celebration" to Icons.Rounded.Celebration,
            "nightlife" to Icons.Rounded.Nightlife,
            "chat" to Icons.AutoMirrored.Rounded.Chat,
        )
    ),
    EventIconCategory(
        "Entertainment & Media", listOf(
            "movie" to Icons.Rounded.Movie,
            "theaters" to Icons.Rounded.Theaters,
            "tv" to Icons.Rounded.Tv,
            "live_tv" to Icons.Rounded.LiveTv,
            "music_note" to Icons.Rounded.MusicNote,
            "library_music" to Icons.Rounded.LibraryMusic,
            "headphones" to Icons.Rounded.Headphones,
            "piano" to Icons.Rounded.Piano,
            "mic" to Icons.Rounded.Mic,
            "videogame_asset" to Icons.Rounded.VideogameAsset,
            "photo_camera" to Icons.Rounded.PhotoCamera,
            "camera" to Icons.Rounded.CameraAlt,
            "collections" to Icons.Rounded.Collections,
        )
    ),
    EventIconCategory(
        "Nature & Pets", listOf(
            "local_florist" to Icons.Rounded.LocalFlorist,
            "park" to Icons.Rounded.Park,
            "forest" to Icons.Rounded.Forest,
            "spa" to Icons.Rounded.Spa,
            "landscape" to Icons.Rounded.Landscape,
            "grass" to Icons.Rounded.Grass,
            "eco" to Icons.Rounded.Eco,
            "wb_sunny" to Icons.Rounded.WbSunny,
            "water_drop" to Icons.Rounded.WaterDrop,
            "pets" to Icons.Rounded.Pets,
        )
    ),
    EventIconCategory(
        "Home & Chores", listOf(
            "home" to Icons.Rounded.Home,
            "house" to Icons.Rounded.House,
            "cottage" to Icons.Rounded.Cottage,
            "chair" to Icons.Rounded.Chair,
            "bed" to Icons.Rounded.Bed,
            "cleaning_services" to Icons.Rounded.CleaningServices,
            "local_laundry_service" to Icons.Rounded.LocalLaundryService,
            "iron" to Icons.Rounded.Iron,
        )
    ),
    EventIconCategory(
        "Goals & Celebrations", listOf(
            "emoji_events" to Icons.Rounded.EmojiEvents,
            "military_tech" to Icons.Rounded.MilitaryTech,
            "workspace_premium" to Icons.Rounded.WorkspacePremium,
            "rocket_launch" to Icons.Rounded.RocketLaunch,
            "stars" to Icons.Rounded.Stars,
            "redeem" to Icons.Rounded.Redeem,
            "ac_unit" to Icons.Rounded.AcUnit,
        )
    ),
)

/** Flat lookup of every event icon by key, derived from [eventIconCategories]. */
val eventIconMap: Map<String, ImageVector> =
    eventIconCategories.flatMap { it.icons }.toMap()

fun iconForName(name: String): ImageVector = eventIconMap[name] ?: Icons.Rounded.Star
