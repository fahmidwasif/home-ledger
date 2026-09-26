package nz.afhome.ledger.data

/** Who paid. The household account is "Anika & Fahmid Home"; every purchase is attributed to a person. */
enum class Person(val label: String) {
    ANIKA("Anika"),
    FAHMID("Fahmid"),
    BOTH("Both / Joint");

    companion object {
        fun of(name: String?): Person = entries.firstOrNull { it.name == name } ?: BOTH
    }
}

/** The account or card a purchase was paid from. */
enum class PayAccount(val label: String, val owner: Person) {
    FAHMID_ANZ("Fahmid ANZ", Person.FAHMID),
    ANIKA_ANZ("Anika ANZ", Person.ANIKA),
    ANIKA_ASB("Anika ASB", Person.ANIKA),
    JOINT_ANZ("Joint ANZ", Person.BOTH),
    JOINT_ASB("Joint ASB", Person.BOTH),
    CASH("Cash", Person.BOTH);

    companion object {
        fun of(name: String?): PayAccount? = entries.firstOrNull { it.name == name }
    }
}

enum class CategoryGroup(val label: String) {
    GROCERY("Groceries"),
    HOUSEHOLD("Household & Personal"),
    CAR("Car"),
    TRANSPORT("Getting Around"),
    LIFESTYLE("Food Out & Fun"),
    WELLBEING("Health & Wellbeing"),
    HOME("Home & Shopping"),
    BILLS("Bills & Housing"),
    SUBSCRIPTIONS("Subscriptions"),
    GIVING("Gifts & Giving"),
    OTHER("Other"),
}

/**
 * Every kind of household spending in NZ. Enum names are stored in the database, so existing
 * names must never be renamed; labels can change freely.
 */
enum class Category(
    val label: String,
    val group: CategoryGroup,
    /** Items in this category go into the home inventory by default. */
    val stocked: Boolean = false,
    val defaultRoom: HomeRoom = HomeRoom.OTHER,
) {
    PRODUCE("Fruit & Veg", CategoryGroup.GROCERY, true, HomeRoom.FRIDGE),
    MEAT("Meat & Fish", CategoryGroup.GROCERY, true, HomeRoom.FREEZER),
    DAIRY("Dairy & Eggs", CategoryGroup.GROCERY, true, HomeRoom.FRIDGE),
    BAKERY("Bread & Bakery", CategoryGroup.GROCERY, true, HomeRoom.KITCHEN),
    PANTRY("Pantry", CategoryGroup.GROCERY, true, HomeRoom.PANTRY),
    DESHI("Bangladeshi & South Asian", CategoryGroup.GROCERY, true, HomeRoom.PANTRY),
    FROZEN("Frozen", CategoryGroup.GROCERY, true, HomeRoom.FREEZER),
    SNACKS("Snacks & Drinks", CategoryGroup.GROCERY, true, HomeRoom.PANTRY),
    HOUSEHOLD("Cleaning & Household", CategoryGroup.HOUSEHOLD, true, HomeRoom.LAUNDRY),
    PERSONAL("Personal Care", CategoryGroup.HOUSEHOLD, true, HomeRoom.BATHROOM),
    HEALTH("Pharmacy & Medicines", CategoryGroup.WELLBEING, true, HomeRoom.BATHROOM),
    MEDICAL("GP, Dental & Optometrist", CategoryGroup.WELLBEING),
    FITNESS("Gym & Sport", CategoryGroup.WELLBEING),
    HAIR_BEAUTY("Haircut & Beauty", CategoryGroup.WELLBEING),
    FUEL("Car – Fuel", CategoryGroup.CAR),
    CAR_CARE("Car – Service, WoF & Parts", CategoryGroup.CAR),
    CAR_ADMIN("Car – Rego, Insurance & RUC", CategoryGroup.CAR),
    PARKING("Parking & Tolls", CategoryGroup.CAR),
    TRANSPORT("AT HOP & Public Transport", CategoryGroup.TRANSPORT),
    TAXI("Uber, Taxi & Scooters", CategoryGroup.TRANSPORT),
    TRAVEL("Travel & Holidays", CategoryGroup.TRANSPORT),
    DINING("Eating Out & Takeaway", CategoryGroup.LIFESTYLE),
    WORK_LUNCH("Bought Lunch at Work", CategoryGroup.LIFESTYLE),
    ENTERTAINMENT("Movies, Events & Outings", CategoryGroup.LIFESTYLE),
    HOBBIES("Hobbies & Books", CategoryGroup.LIFESTYLE),
    SUBSCRIPTIONS("Streaming & App Subscriptions", CategoryGroup.SUBSCRIPTIONS),
    CLOTHING("Clothing & Shoes", CategoryGroup.HOME),
    HOME_GARDEN("Home & Garden", CategoryGroup.HOME, true, HomeRoom.STORAGE),
    FURNITURE("Furniture & Appliances", CategoryGroup.HOME),
    ELECTRONICS("Gadgets & Electronics", CategoryGroup.HOME),
    EDUCATION("Education & Courses", CategoryGroup.HOME),
    UTILITIES("Power & Gas", CategoryGroup.BILLS),
    INTERNET_PHONE("Internet & Mobile", CategoryGroup.BILLS),
    WATER_RATES("Water & Council Rates", CategoryGroup.BILLS),
    RENT("Rent / Mortgage", CategoryGroup.BILLS),
    INSURANCE("Insurance (Home, Contents, Health, Life)", CategoryGroup.BILLS),
    FEES("Bank Fees & Interest", CategoryGroup.BILLS),
    LOANS("Loan & Credit Card Repayments", CategoryGroup.BILLS),
    TAX("Tax, IRD & Government", CategoryGroup.BILLS),
    GIFTS("Gifts", CategoryGroup.GIVING),
    CHARITY("Zakat, Sadaqah & Donations", CategoryGroup.GIVING),
    FAMILY_SUPPORT("Family Support / Remittance", CategoryGroup.GIVING),
    OTHER("Other", CategoryGroup.OTHER);

    companion object {
        fun of(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Where an item lives at home, so it can be found later. */
enum class HomeRoom(val label: String) {
    PANTRY("Pantry"),
    KITCHEN("Kitchen cupboard / bench"),
    FRIDGE("Fridge"),
    FREEZER("Freezer"),
    BATHROOM("Bathroom"),
    LAUNDRY("Laundry"),
    BEDROOM("Bedroom"),
    LIVING("Living room"),
    STORAGE("Hallway / storage cupboard"),
    GARAGE("Garage / car"),
    OTHER("Other");

    companion object {
        fun of(name: String?): HomeRoom = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Why something was taken out of the inventory. LUNCHBOX tracks food packed for work. */
enum class UsePurpose(val label: String) {
    HOME_MEAL("Cooking at home"),
    LUNCHBOX("Packed lunch for work"),
    GENERAL("General use"),
    GIFTED("Given away"),
    WASTED("Expired / thrown out");

    companion object {
        fun of(name: String?): UsePurpose = entries.firstOrNull { it.name == name } ?: GENERAL
    }
}

enum class ShoppingSource(val label: String) {
    MANUAL("Added"), PREDICTED("Usually due"), LOW_STOCK("Running low"), USED_UP("Used up");

    companion object {
        fun of(name: String?): ShoppingSource = entries.firstOrNull { it.name == name } ?: MANUAL
    }
}

val GIFT_OCCASIONS = listOf("Eid", "Birthday", "Wedding", "Baby / Aqiqah", "Christmas", "Housewarming", "Anniversary", "Thank you", "Other")

/** How often a recurring payment comes out. */
enum class Frequency(val label: String, val perYear: Double) {
    WEEKLY("Weekly", 52.0),
    FORTNIGHTLY("Fortnightly", 26.0),
    MONTHLY("Monthly", 12.0),
    QUARTERLY("Every 3 months", 4.0),
    YEARLY("Yearly", 1.0);

    fun next(day: Long): Long {
        val d = java.time.LocalDate.ofEpochDay(day)
        return when (this) {
            WEEKLY -> d.plusWeeks(1)
            FORTNIGHTLY -> d.plusWeeks(2)
            MONTHLY -> d.plusMonths(1)
            QUARTERLY -> d.plusMonths(3)
            YEARLY -> d.plusYears(1)
        }.toEpochDay()
    }

    companion object {
        fun of(name: String?): Frequency = entries.firstOrNull { it.name == name } ?: MONTHLY
    }
}
