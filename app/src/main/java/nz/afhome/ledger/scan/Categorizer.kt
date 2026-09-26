package nz.afhome.ledger.scan

import nz.afhome.ledger.data.Category
import nz.afhome.ledger.data.Category.*

/**
 * Keyword categoriser tuned for Auckland shops and a Bangladeshi household.
 * Order of precedence: rules learned from the user's own corrections → item keywords → store default.
 */
class Categorizer(private val learned: Map<String, Category> = emptyMap()) {

    fun categorize(normName: String, store: String?): Category {
        learned[normName]?.let { return it }
        val itemHit = bestKeyword(normName, ITEM_KEYWORDS)
        val storeCat = storeCategory(store)
        // Non-grocery stores (fuel, pharmacy, hardware...) decide the category unless an item keyword is clearly specific.
        return when {
            itemHit != null -> itemHit
            storeCat != null -> storeCat
            else -> OTHER
        }
    }

    companion object {
        private fun bestKeyword(text: String, table: Map<Category, List<String>>): Category? {
            var best: Category? = null
            var bestLen = 0
            val padded = " $text "
            for ((cat, words) in table) for (w in words) {
                // Deshi words are distinctive ("hilsa", "masoor"), so they beat generic ones like "frozen" or "fish".
                val score = w.length + if (cat == DESHI) 4 else 0
                if (score > bestLen && padded.contains(" $w")) { best = cat; bestLen = score }
            }
            return best
        }

        fun storeCategory(store: String?): Category? {
            val s = " " + (store ?: return null).lowercase() + " "
            return bestKeyword(s.trim(), STORE_KEYWORDS)
        }

        fun isFuelStation(store: String?): Boolean = storeCategory(store) == FUEL

        fun isGroceryStore(store: String?): Boolean {
            val s = store?.lowercase() ?: return false
            return GROCERY_STORES.any { s.contains(it) }
        }

        val GROCERY_STORES = listOf(
            "woolworths", "countdown", "pak n save", "paknsave", "pak'nsave", "new world", "fresh choice", "freshchoice",
            "supervalue", "four square", "foodstuffs", "costco", "farro", "raeward", "moore wilson", "tai ping", "lucky",
            "spice", "halal", "bangla", "desi", "indian", "asian", "mahi", "fruit world", "produce",
        )

        val STORE_KEYWORDS: Map<Category, List<String>> = mapOf(
            FUEL to listOf("z energy", "z ", "bp ", "bp connect", "mobil", "gull", "waitomo", "caltex", "npd", "challenge", "costco fuel", "allied", "tasman fuel", "rd petroleum"),
            CAR_CARE to listOf("repco", "supercheap", "vtnz", "vinz", "aa centre", "aa auto", "midas", "tony's tyre", "tyre", "beaurepaires", "bridgestone", "firestone", "pit stop", "mechanic", "auto electrical", "car wash"),
            PARKING to listOf("wilson parking", "at parking", "parking", "care park", "secure parking", "toll"),
            TRANSPORT to listOf("at hop", "auckland transport", "metro"),
            HEALTH to listOf("chemist warehouse", "unichem", "life pharmacy", "pharmacy", "medical", "health 2000", "bargain chemist", "dental", "optometrist", "specsavers"),
            HOME_GARDEN to listOf("bunnings", "mitre 10", "mitre10", "placemakers", "kings plant", "palmers", "briscoes", "bed bath", "freedom furniture", "harvey norman home", "kmart", "the warehouse", "spotlight", "animates", "petstock"),
            ELECTRONICS to listOf("noel leeming", "jb hi-fi", "jb hi fi", "harvey norman", "pb tech", "apple", "samsung", "mighty ape"),
            CLOTHING to listOf("farmers", "glassons", "hallenstein", "h&m", "h & m", "uniqlo", "cotton on", "rebel sport", "kathmandu", "hannahs", "number one shoes", "platypus", "postie", "max fashions"),
            DINING to listOf("mcdonald", "kfc", "burger king", "subway", "domino", "pizza hut", "hell pizza", "starbucks", "uber eats", "ubereats", "doordash", "delivereasy", "restaurant", "cafe", "café", "biryani", "kebab", "sushi", "food court", "noodle", "grill", "bakehouse", "carl's jr", "wendy", "nando"),
            ENTERTAINMENT to listOf("event cinemas", "hoyts", "reading cinemas", "netflix", "spotify", "neon", "disney", "sky tv", "ticketmaster", "rainbow's end", "rainbows end", "kelly tarlton"),
            UTILITIES to listOf("mercury", "genesis", "contact energy", "meridian", "electric kiwi", "powershop", "one nz", "spark", "2degrees", "skinny", "vector", "watercare", "slingshot", "orcon"),
            CAR_ADMIN to listOf("nzta", "waka kotahi", "nz transport agency", "aa insurance", "ami", "state insurance", "tower insurance", "aa membership"),
            FAMILY_SUPPORT to listOf("western union", "remitly", "wise", "xe money", "bkash"),
        )

        val ITEM_KEYWORDS: Map<Category, List<String>> = mapOf(
            // Bangladeshi / South Asian staples — checked first by length so "mustard oil" beats "oil".
            DESHI to listOf(
                "dal", "daal", "masoor", "moong", "mung", "chana", "chola", "urad", "toor", "matar dal",
                "chinigura", "kalijira", "kalizira", "basmati", "atta", "chakki", "besan", "suji", "sooji", "semolina",
                "chira", "chida", "poha", "muri", "puffed rice", "chanachur", "jhal muri", "bombay mix",
                "ilish", "hilsa", "rui", "rohu", "katla", "pangas", "pabda", "koi fish", "shutki", "loitta", "tengra", "boal", "chingri",
                "panch phoron", "panchphoron", "kalonji", "nigella", "kalo jira", "jeera", "cumin", "haldi", "turmeric", "garam masala",
                "biryani masala", "radhuni", "shan", "mdh", "everest", "national", "ahmed", "bd food", "pran", "radhuni",
                "mustard oil", "shorisha", "sorisha", "ghee", "paneer", "gur", "jaggery", "khejur", "date molasses",
                "paratha", "porota", "roti", "naan", "chapati", "papad", "achar", "pickle", "tamarind", "tetul",
                "lau", "korola", "karela", "bitter gourd", "potol", "dhonepata", "coriander", "green chilli",
                "kacha morich", "shorisha", "tejpata", "bay leaf", "elachi", "cardamom", "darchini", "cinnamon", "lobongo", "cloves",
                "rooh afza", "tang", "shemai", "vermicelli", "lachha", "sewai", "mishti", "rasgulla", "roshogolla", "gulab jamun", "sandesh",
                "fuchka", "chotpoti", "pitha", "haleem", "nihari", "tandoori", "tikka",
            ),
            PRODUCE to listOf(
                "apple", "banana", "orange", "mandarin", "kiwifruit", "kiwi fruit", "grape", "pear", "avocado", "lemon", "lime", "mango",
                "berries", "strawberr", "blueberr", "pineapple", "melon", "watermelon", "feijoa", "tamarillo", "plum", "peach", "nectarine",
                "potato", "kumara", "onion", "garlic", "ginger", "tomato", "carrot", "cabbage", "cauli", "broccoli", "capsicum",
                "cucumber", "lettuce", "spinach", "silverbeet", "pumpkin", "courgette", "zucchini", "eggplant", "aubergine", "brinjal",
                "beans", "okra", "bhindi", "radish", "mushroom", "celery", "leek", "herbs", "mint", "salad", "fresh veg", "veg", "fruit",
            ),
            MEAT to listOf(
                "chicken", "beef", "lamb", "mutton", "goat", "mince", "steak", "drumstick", "thigh", "breast", "wings", "sausage",
                "salmon", "fish", "tuna", "hoki", "tarakihi", "snapper", "prawn", "shrimp", "mussel", "fillet", "meat",
            ),
            DAIRY to listOf(
                "milk", "anchor", "lite blue", "blue top", "yoghurt", "yogurt", "cheese", "butter", "cream", "egg", "eggs", "sour cream",
                "mainland", "lewis road", "a2", "meadow fresh", "puhoi", "custard",
            ),
            BAKERY to listOf("bread", "loaf", "vogel", "tip top", "burger buns", "buns", "wrap", "tortilla", "bagel", "croissant", "muffin", "pita", "bakery"),
            FROZEN to listOf("frozen", "ice cream", "icecream", "peas", "mixed veg", "fries", "chips frozen", "hash brown", "wattie", "tip top ice", "memphis"),
            PANTRY to listOf(
                "rice", "flour", "sugar", "salt", "oil", "olive oil", "canola", "vinegar", "sauce", "tomato sauce", "soy sauce", "pasta",
                "noodle", "maggi", "indomie", "spaghetti", "cereal", "weet-bix", "weetbix", "oats", "muesli", "honey", "jam", "peanut butter",
                "marmite", "vegemite", "baked beans", "tinned", "canned", "coconut milk", "stock", "spice", "pepper", "chilli powder",
                "baking", "yeast", "lentil", "chickpea", "tea", "coffee", "milo", "dilmah", "bell tea", "nescafe",
            ),
            SNACKS to listOf(
                "chips", "crisps", "biscuit", "cookie", "chocolate", "whittaker", "cadbury", "lollies", "candy", "cracker", "popcorn",
                "nuts", "cashew", "almond", "juice", "soft drink", "coke", "pepsi", "sprite", "lemonade", "water", "l&p", "energy drink",
                "fanta", "v energy", "powerade", "snack",
            ),
            HOUSEHOLD to listOf(
                "detergent", "dishwash", "finish", "fairy", "sunlight", "laundry", "persil", "omo", "fabric softener", "bleach", "janola",
                "toilet paper", "toilet tissue", "paper towel", "tissues", "rubbish bag", "bin liner", "glad", "cling", "foil", "baking paper",
                "sponge", "scrub", "spray", "wipes", "cleaner", "handee", "sorbent", "purex", "napisan", "dettol", "batteries", "light bulb",
            ),
            PERSONAL to listOf(
                "shampoo", "conditioner", "soap", "body wash", "toothpaste", "toothbrush", "colgate", "deodorant", "razor", "shaving",
                "lotion", "moisturiser", "sunscreen", "sanitary", "tampon", "pads", "nappies", "cotton", "floss", "mouthwash", "hair",
                "makeup", "lipstick", "nail",
            ),
            HEALTH to listOf("panadol", "paracetamol", "ibuprofen", "nurofen", "vitamin", "supplement", "prescription", "rx", "bandaid", "plaster", "antihistamine", "cough", "cold and flu", "medicine"),
            FUEL to listOf("unleaded", "petrol", "diesel", "91 ", "95 ", "98 ", "zx premium", "fuel", "pump "),
            CAR_CARE to listOf("wof", "warrant of fitness", "oil change", "service", "tyre", "wiper", "coolant", "engine oil", "brake"),
            CAR_ADMIN to listOf("registration", "rego", "vehicle licence", "ruc", "road user"),
            PARKING to listOf("parking", "toll"),
            GIFTS to listOf("gift card", "giftcard", "gift wrap", "wrapping paper", "greeting card", "card birthday", "flowers", "bouquet"),
            CLOTHING to listOf("shirt", "t-shirt", "tee", "jeans", "trousers", "dress", "saree", "salwar", "kurta", "panjabi", "hijab", "socks", "jacket", "shoes", "sneakers", "jumper"),
            ELECTRONICS to listOf("charger", "cable", "usb", "headphone", "earbuds", "phone case", "hdmi"),
            HOME_GARDEN to listOf("plant", "potting mix", "compost", "seeds", "garden", "hose", "screw", "paint", "storage box", "container", "hanger", "towel", "pillow", "sheet", "duvet"),
        )
    }
}
