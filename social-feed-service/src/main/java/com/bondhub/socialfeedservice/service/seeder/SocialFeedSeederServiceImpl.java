package com.bondhub.socialfeedservice.service.seeder;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.event.socialfeed.InteractionType;
import com.bondhub.socialfeedservice.client.AuthServiceClient;
import com.bondhub.socialfeedservice.client.PostRecommendationClient;
import com.bondhub.socialfeedservice.client.UserServiceClient;
import com.bondhub.socialfeedservice.dto.request.user.UserInterestSeedUpdateRequest;
import com.bondhub.socialfeedservice.dto.response.account.AccountSummaryResponse;
import com.bondhub.socialfeedservice.model.Comment;
import com.bondhub.socialfeedservice.model.Hashtag;
import com.bondhub.socialfeedservice.model.Post;
import com.bondhub.socialfeedservice.model.Reaction;
import com.bondhub.socialfeedservice.model.UserInteraction;
import com.bondhub.socialfeedservice.model.embedded.PostContent;
import com.bondhub.socialfeedservice.model.embedded.PostMedia;
import com.bondhub.socialfeedservice.model.embedded.PostMusic;
import com.bondhub.socialfeedservice.model.embedded.PostStats;
import com.bondhub.socialfeedservice.model.enums.PostType;
import com.bondhub.socialfeedservice.model.enums.ReactionTargetType;
import com.bondhub.socialfeedservice.model.enums.ReactionType;
import com.bondhub.socialfeedservice.model.enums.Visibility;
import com.bondhub.socialfeedservice.publisher.PostEventPublisher;
import com.bondhub.socialfeedservice.repository.CommentRepository;
import com.bondhub.socialfeedservice.repository.HashtagRepository;
import com.bondhub.socialfeedservice.repository.PostRepository;
import com.bondhub.socialfeedservice.repository.ReactionRepository;
import com.bondhub.socialfeedservice.repository.UserInteractionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Improved seeder that generates ~10 000 posts organised into 30 themed content buckets.
 *
 * <h3>Design goals</h3>
 * <ul>
 *   <li><b>Theme cohesion</b>: each post's caption, hashtags, and location align with one of 30
 *       interest themes (travel, fitness, tech, food, …).  This gives the recommendation engine's
 *       embedding model dense, distinguishable semantic clusters to work with.</li>
 *   <li><b>Weighted post-type distribution</b>: 55% FEED / 20% REEL / 20% SHARE / 5% STORY.
 *       STORYs are ephemeral so most seeded posts should be long-lived FEED/REEL.</li>
 *   <li><b>Realistic, skewed engagement</b>: a small fraction of posts are marked as "trending"
 *       (high reaction / view counts).  This exercises the RRF popularity-score path.</li>
 *   <li><b>Recency spread</b>: {@code uploadedAt} is spread uniformly over the past 90 days so
 *       the exponential-decay scorer sees a natural distribution.</li>
 *   <li><b>Batch saves</b>: posts are persisted in chunks of {@value BATCH_SIZE} to avoid
 *       MongoDB driver memory pressure at 10k documents.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SocialFeedSeederServiceImpl implements SocialFeedSeederService {

    // ── Tuning knobs ─────────────────────────────────────────────────────────

    /** Total posts to generate (excluding auto-generated SHARE posts). */
    static final int TARGET_POSTS         = 700;
    /** SHARE posts are generated as a fraction of saved base posts. */
    static final int SHARE_POST_TARGET    = 200;
    /** Mongo saveAll chunk size — keeps heap pressure low. */
    static final int BATCH_SIZE           = 100;

    static final int MAX_ACCOUNTS         = 20;
    static final int INTERESTS_PER_USER   = 5;

    static final int COMMENTS_PER_POST    = 3;
    static final int REACTIONS_PER_POST   = 6;
    static final int INTERACTIONS_PER_POST = 10;

    /** Fraction of posts that receive "viral" engagement multipliers (0.0–1.0). */
    static final double TRENDING_FRACTION = 0.05;
    static final int    TRENDING_REACTION_MULTIPLIER = 20;
    static final int    TRENDING_VIEW_MULTIPLIER     = 100;

    static final int RECENCY_SPREAD_DAYS  = 90;

    // ── Weighted post-type distribution ──────────────────────────────────────
    // FEED=55%, REEL=20%, STORY=5% (base), remaining become SHARE in step 2
    static final int FEED_FRACTION  = 55;
    static final int REEL_FRACTION  = 20;
    static final int STORY_FRACTION = 5;

    // ── Media pools ──────────────────────────────────────────────────────────

    static final String[] IMAGE_URLS = {
            "https://picsum.photos/seed/bb-1/1200/800",  "https://picsum.photos/seed/bb-2/1200/800",
            "https://picsum.photos/seed/bb-3/1200/800",  "https://picsum.photos/seed/bb-4/1200/800",
            "https://picsum.photos/seed/bb-5/1200/800",  "https://picsum.photos/seed/bb-6/1200/800",
            "https://picsum.photos/seed/bb-7/1200/800",  "https://picsum.photos/seed/bb-8/1200/800",
            "https://picsum.photos/seed/bb-9/1200/800",  "https://picsum.photos/seed/bb-10/1200/800",
            "https://picsum.photos/seed/bb-11/1200/800", "https://picsum.photos/seed/bb-12/1200/800",
            "https://picsum.photos/seed/bb-13/1200/800", "https://picsum.photos/seed/bb-14/1200/800",
            "https://picsum.photos/seed/bb-15/1200/800", "https://picsum.photos/seed/bb-16/1200/800",
            "https://picsum.photos/seed/bb-17/1200/800", "https://picsum.photos/seed/bb-18/1200/800",
            "https://picsum.photos/seed/bb-19/1200/800", "https://picsum.photos/seed/bb-20/1200/800",
    };

    static final String[] VIDEO_URLS = {
            "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4",
            "https://filesamples.com/samples/video/mp4/sample_640x360.mp4",
            "https://filesamples.com/samples/video/mp4/sample_1280x720_surfing_with_audio.mp4",
            "https://filesamples.com/samples/video/mp4/sample_960x400_ocean_with_audio.mp4",
            "https://samplelib.com/lib/preview/mp4/sample-5s.mp4",
            "https://samplelib.com/lib/preview/mp4/sample-10s.mp4",
            "https://samplelib.com/lib/preview/mp4/sample-15s.mp4",
            "https://samplelib.com/lib/preview/mp4/sample-20s.mp4",
            "https://www.w3schools.com/html/mov_bbb.mp4",
            "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
            "https://download.samplelib.com/mp4/sample-5s.mp4",
            "https://download.samplelib.com/mp4/sample-10s.mp4",
    };

    // ── Comment pool ─────────────────────────────────────────────────────────

    static final String[] COMMENT_TEXTS = {
            "This is exactly the content I needed today! 🙌",
            "Wow, where is this? It looks stunning! 😍",
            "You always make this look so effortless 👏",
            "I need to try this ASAP!",
            "Literally laughed out loud 😂",
            "So relatable, I felt this in my soul.",
            "The vibe is immaculate 🔥",
            "Drop the recipe please!! 🙏",
            "Goals. Absolute goals.",
            "This just made my day 😊",
            "You inspire me every single time 💫",
            "Not me saving this for later 👀",
            "Okay but how?? Spill the secrets!",
            "This brought me so much joy ❤️",
            "Same energy every single day 💯",
            "Best thing I've seen all week!",
            "Can't stop rewatching this 🔁",
            "Tag someone who needs to see this 👇",
            "Following for more! 🚀",
            "This deserves way more views 👆",
    };

    // ── 30 themed content buckets ─────────────────────────────────────────────
    //
    // Each bucket defines: theme name, hashtags (3-6 closely related tags),
    // several caption templates, and a preferred post type (null → random).
    //
    // Hashtags use the '#' prefix so they look realistic in the caption text;
    // seedHashtags() will strip it for the normalizedValue.

    record ThemeBucket(
            String theme,
            List<String> hashtags,
            List<String> captions,
            PostType preferredType   // null = choose by weighted distribution
    ) {}

    static final List<ThemeBucket> THEMES = List.of(

        // ─ Lifestyle ──────────────────────────────────────────────────────────
        new ThemeBucket("travel",
            List.of("#travel", "#wanderlust", "#adventure", "#explore", "#backpacker"),
            List.of(
                "Just landed in a new city and already in love ✈️ #travel #wanderlust",
                "Every trip teaches me something new about myself 🌍 #explore #adventure",
                "Packed my bag and left with no plan – best decision ever 🎒 #backpacker",
                "Local street food > any fancy restaurant 🍜 #travel #foodie",
                "Sunsets hit different when you're thousands of miles from home 🌅 #wanderlust"
            ), null),

        new ThemeBucket("photography",
            List.of("#photography", "#goldenHour", "#streetPhoto", "#portrait", "#shotOnPhone"),
            List.of(
                "Golden hour was absolutely unreal today 🌅 #photography #goldenHour",
                "Found the perfect light and the camera gods rewarded me 📸 #portrait",
                "Street photography is just therapy with a camera 🎞️ #streetPhoto",
                "No filters needed when the scene is this good ✨ #shotOnPhone",
                "Spent 2 hours waiting for this one shot. Worth it. 🏆 #photography"
            ), PostType.FEED),

        new ThemeBucket("fitness",
            List.of("#fitness", "#workout", "#gymLife", "#noExcuses", "#healthyLifestyle"),
            List.of(
                "Finally finished my home gym setup. No more excuses! 💪 #fitness #gymLife",
                "New personal best on the 5k run this morning 🏃‍♂️ #fitness #noExcuses",
                "Rest days are part of the plan too. Recovery matters 🧘 #healthyLifestyle",
                "Pre-workout hits different at 5 AM 🌄 #gymLife #fitness",
                "The only bad workout is the one that didn't happen 🔥 #noExcuses"
            ), PostType.REEL),

        new ThemeBucket("cooking",
            List.of("#cooking", "#homemadeFood", "#recipe", "#foodie", "#mealPrep"),
            List.of(
                "Made homemade ramen from scratch tonight — proud of myself 🍥 #cooking #homemadeFood",
                "Meal prepping on Sunday so future me doesn't order takeout all week 🥗 #mealPrep",
                "Drop a 🙋 if you cook better than any restaurant in your area 🍳 #recipe",
                "Three ingredients, thirty minutes, zero complaints 🍝 #cooking #foodie",
                "Baking bread on a rainy day is peak comfort 🍞 #homemadeFood"
            ), PostType.FEED),

        new ThemeBucket("coffee",
            List.of("#coffee", "#coffeeLover", "#latteart", "#cafeCulture", "#morningCoffee"),
            List.of(
                "Tried a new coffee shop downtown — 10/10 would recommend ☕ #coffeeLover",
                "Latte art is my love language ☕ #latteart #cafeCulture",
                "Running on caffeine and optimism ☀️ #morningCoffee #coffee",
                "No meeting is bad enough that coffee can't fix it 😅 #coffeeLover",
                "Third coffee of the day — totally normal 🎯 #coffee"
            ), PostType.FEED),

        new ThemeBucket("technology",
            List.of("#tech", "#software", "#AI", "#coding", "#developer"),
            List.of(
                "That feeling when your code finally compiles on the first try 🎉 #coding #developer",
                "AI just wrote better code than me. Time to re-skill 🤖 #AI #tech",
                "The best debugger is a rubber duck and some patience 🦆 #software",
                "Dark mode is not a preference, it is a lifestyle 🌑 #developer",
                "Side project update: it works on my machine™ 💻 #tech #software"
            ), PostType.FEED),

        new ThemeBucket("artificialIntelligence",
            List.of("#AI", "#machinelearning", "#deepLearning", "#llm", "#generativeAI"),
            List.of(
                "LLMs in 2025 feel like magic, until you read the paper 📄 #AI #llm",
                "Just fine-tuned my first model on custom data — rabbit hole unlocked 🕳️ #deepLearning",
                "Generative AI is changing every creative workflow I know 🎨 #generativeAI #AI",
                "The gap between research and production is still enormous 🏗️ #machinelearning",
                "Embeddings make me think differently about meaning itself 🧠 #AI #deepLearning"
            ), PostType.REEL),

        new ThemeBucket("gaming",
            List.of("#gaming", "#gamerLife", "#rpg", "#esports", "#indieGames"),
            List.of(
                "Stayed up until 3am for 'just one more level' 🎮 #gaming #gamerLife",
                "This indie studio just dropped the game of the year and nobody's talking about it 👾 #indieGames",
                "Speedrun PB shattered today. The grind pays off 🏆 #gaming #esports",
                "RPG lore is just another name for procrastination 📖 #rpg #gamerLife",
                "Co-op with friends > single player. Change my mind. 🕹️ #gaming"
            ), PostType.REEL),

        new ThemeBucket("music",
            List.of("#music", "#newMusic", "#playlist", "#vibes", "#liveMusic"),
            List.of(
                "Current mood: lofi beats + rainy window + hot tea 🎵☕ #music #vibes",
                "This album just rewired my brain. On repeat all week 🎧 #newMusic #playlist",
                "Live music is the only live experience that cannot be streamed 🎸 #liveMusic",
                "Added 47 songs to my playlist this week. Highly productive. 🎶 #music #vibes",
                "That one song that hits different at 2am 🌙 #playlist #music"
            ), PostType.REEL),

        new ThemeBucket("movies",
            List.of("#movies", "#filmReview", "#cinematography", "#watchlist", "#streaming"),
            List.of(
                "Binge-watched the entire series in one weekend. No regrets. 📺 #movies #streaming",
                "The cinematography in this film is a masterclass in visual storytelling 🎬 #cinematography",
                "Movies that need a second watch to fully understand 🤯 #filmReview #movies",
                "Nothing like a good rainy-day film marathon 🌧️🍿 #watchlist",
                "That plot twist hit different the second time around 🔄 #movies #filmReview"
            ), PostType.FEED),

        new ThemeBucket("reading",
            List.of("#reading", "#bookstagram", "#bookReview", "#fiction", "#selfImprovement"),
            List.of(
                "Just discovered a hidden gem of a bookstore in the old quarter 📚 #reading #bookstagram",
                "Finished a 400-page novel in two days. No I'm not okay 📖 #fiction",
                "Non-fiction that actually changed how I think about money 💡 #selfImprovement #reading",
                "Bookmarks are just reminders that you have better intentions than discipline 😅 #bookstagram",
                "Five star read alert — dropping the title in comments 🌟 #bookReview #reading"
            ), PostType.FEED),

        new ThemeBucket("art",
            List.of("#art", "#digitalArt", "#illustration", "#creativeProcess", "#sketch"),
            List.of(
                "Art exhibition today was so inspiring — highly recommend going 🎨 #art",
                "Spent 6 hours on this sketch and my hand agrees 🖊️ #illustration #sketch",
                "Digital art workflow timelapse — always amazed when it comes together 💻 #digitalArt",
                "The blank canvas is the scariest and most exciting thing 🎭 #creativeProcess #art",
                "Commissioned piece finished! Client was happy, that's all that matters 🙌 #illustration"
            ), PostType.REEL),

        new ThemeBucket("hiking",
            List.of("#hiking", "#outdoors", "#trailLife", "#mountains", "#natureLover"),
            List.of(
                "Weekend hike with the squad — views were worth every step 🏔️ #hiking #outdoors",
                "There is no Wi-Fi in the forest but I promise the connection is better 🌲 #natureLover",
                "Sunrise from the summit. This is what we train for 🌄 #mountains #hiking",
                "Trail conditions: muddy, beautiful, 10/10 would suffer again 😂 #trailLife",
                "Packing for a 3-day backpacking trip. Light is right 🎒 #hiking #outdoors"
            ), PostType.FEED),

        new ThemeBucket("yoga",
            List.of("#yoga", "#mindfulness", "#morningRoutine", "#breathe", "#flexibility"),
            List.of(
                "Unplugged from socials for 3 days. Came back feeling refreshed ✨ #mindfulness",
                "30 days of morning yoga — the before/after might surprise you 🧘 #yoga #morningRoutine",
                "The hardest pose is learning to rest without guilt 🌿 #breathe #yoga",
                "Props are not cheating — they are just wisdom 🧱 #yoga #flexibility",
                "Meditation at sunset is my free therapy session 💜 #mindfulness #yoga"
            ), PostType.REEL),

        new ThemeBucket("cycling",
            List.of("#cycling", "#roadBike", "#bikePacking", "#veloCulture", "#fixedGear"),
            List.of(
                "100km done before noon. The legs don't lie 🚴 #cycling #roadBike",
                "Bike-packing trip across the highlands — best week of my life 🏕️ #bikePacking",
                "Fixed gear in the city is just organised chaos 🎢 #fixedGear #cycling",
                "The commute is the workout. No gym required. 💪 #veloCulture #cycling",
                "Hill climbing: masochism with great scenery 😅 #roadBike #cycling"
            ), PostType.FEED),

        new ThemeBucket("football",
            List.of("#football", "#matchDay", "#soccer", "#goals", "#fanZone"),
            List.of(
                "That last-minute winner still giving me chills ⚽ #football #matchDay",
                "Tactics board session — the strategy geek in me loves this 📋 #soccer #football",
                "Weekend league: scored the opener, team won, life is good 🙌 #goals #football",
                "Football is 90% mental and 100% beautiful 💫 #football #soccer",
                "Derby day energy is unlike anything else in sport 🔥 #matchDay #fanZone"
            ), PostType.REEL),

        new ThemeBucket("pets",
            List.of("#pets", "#catLovers", "#dogLife", "#petTax", "#animalLovers"),
            List.of(
                "Pet tax: my cat judges me 24/7 and I love it 🐱 #petTax #catLovers",
                "Dog decided the middle of my laptop is the best nap spot 🐶 #dogLife #pets",
                "This animal has more personality than most people I know 😂 #animalLovers #pets",
                "Adoption anniversary! Best decision we ever made 🐾 #pets #petTax",
                "When they tilt their head and all your problems disappear 🥺 #catLovers #dogLife"
            ), PostType.FEED),

        new ThemeBucket("streetFood",
            List.of("#streetFood", "#foodTour", "#localEats", "#foodie", "#eatingOut"),
            List.of(
                "Best pho I've ever had from a cart that costs less than a coffee ☕🍜 #streetFood #localEats",
                "24-hour food tour of the city: highlights in thread form 🧵 #foodTour #foodie",
                "The best restaurants don't have Michelin stars — fight me 🔥 #eatingOut #streetFood",
                "Street tacos at midnight slap differently 🌮 #streetFood #foodie",
                "Found this hidden stall tucked in an alley. 10 stars ⭐ #localEats #streetFood"
            ), PostType.FEED),

        new ThemeBucket("fashion",
            List.of("#fashion", "#ootd", "#streetStyle", "#vintage", "#sustainable"),
            List.of(
                "Thrift store haul turned into the best outfit I've ever built 👗 #vintage #fashion",
                "OOTD: dressed for the job I want, not the office I'm in 💼 #ootd #streetStyle",
                "Sustainable fashion brands that actually look good — sharing my list 🌱 #sustainable #fashion",
                "The jacket has been waiting in my cart for 3 months. Today was the day 🛒 #ootd",
                "Vintage finds hit different when they still have original tags 🏷️ #vintage #streetStyle"
            ), PostType.FEED),

        new ThemeBucket("mentalHealth",
            List.of("#mentalHealth", "#selfCare", "#therapy", "#wellbeing", "#burnout"),
            List.of(
                "Reminder: rest is productive. You are not a machine 🌿 #mentalHealth #selfCare",
                "Boundaries are not walls — they're just the architecture of healthy relationships 🤝 #wellbeing",
                "Started therapy six months ago. Sharing my honest experience 💬 #therapy #mentalHealth",
                "Burnout is real and underdiagnosed. Take the day off. 🛋️ #burnout #selfCare",
                "Progress is not linear and that is perfectly okay 💙 #mentalHealth #wellbeing"
            ), PostType.FEED),

        new ThemeBucket("entrepreneurship",
            List.of("#entrepreneurship", "#startupLife", "#founderStory", "#hustle", "#buildinPublic"),
            List.of(
                "Month 6 as a solo founder: what nobody told me 📊 #entrepreneurship #founderStory",
                "Building in public — shipped a new feature today and users love it 🚀 #buildinPublic",
                "Revenue milestone unlocked. Still pinching myself 💸 #startupLife #hustle",
                "The idea is the easy part. Execution is everything. 🎯 #entrepreneurship",
                "Cold email reply rate went from 2% to 18% after one change 📧 #startupLife #founderStory"
            ), PostType.FEED),

        new ThemeBucket("sustainableLiving",
            List.of("#sustainable", "#zeroWaste", "#ecofriendly", "#greenliving", "#climateAction"),
            List.of(
                "Switched to zero-waste shopping — harder than expected, worth it 🛍️ #zeroWaste #sustainable",
                "30-day no-buy challenge: what I learned about consumption 🌿 #greenliving",
                "Solar panels installed. Electricity bill dissolved. Science is cool ☀️ #ecofriendly #climateAction",
                "Composting: from kitchen scraps to garden gold 🌱 #zeroWaste #sustainable",
                "Fast fashion stats that genuinely changed how I shop 📉 #sustainable #ecofriendly"
            ), PostType.FEED),

        new ThemeBucket("interiorDesign",
            List.of("#interiorDesign", "#homeDecor", "#minimalDesign", "#renovate", "#cozyhome"),
            List.of(
                "Spent three days repainting this room and I would do it again instantly 🎨 #interiorDesign #homeDecor",
                "Minimalist shelf styling — proving less really is more 🪴 #minimalDesign",
                "Before & after of our bedroom renovation: the power of paint 🏠 #renovate #interiorDesign",
                "Thrifted furniture, a few plants, and suddenly it's a magazine spread 🌿 #cozyhome #homeDecor",
                "Living room progress journal — documenting every step 🔨 #renovate #interiorDesign"
            ), PostType.FEED),

        new ThemeBucket("basketball",
            List.of("#basketball", "#hoops", "#nba", "#streetball", "#ballIsLife"),
            List.of(
                "First dunk in a pickup game. The moment has been ten years in the making 🏀 #basketball #hoops",
                "This crossover is sending defenders to another zip code 💨 #streetball #hoops",
                "NBA draft analysis thread — who's your pick? 🔍 #nba #basketball",
                "3-on-3 at dusk with blacktop courts and the right crew hits different 🌆 #streetball",
                "Shot selection is the most underrated skill in basketball 🎯 #ballIsLife #basketball"
            ), PostType.REEL),

        new ThemeBucket("baking",
            List.of("#baking", "#sourdough", "#pastry", "#cakes", "#bakingTherapy"),
            List.of(
                "Baking bread on a rainy day is peak comfort 🍞 #baking #sourdough",
                "Levain maintenance Sunday: my starter is thriving 🦠 #sourdough #baking",
                "Croissant lamination took 3 days but the layers are PERFECT 🥐 #pastry #bakingTherapy",
                "Birthday cake for a friend: 4 layers, 3 flavours, 1 very happy face 🎂 #cakes #baking",
                "When the crumb structure comes out exactly like the recipe photo 😭✨ #sourdough #baking"
            ), PostType.FEED),

        new ThemeBucket("webDevelopment",
            List.of("#webDev", "#frontend", "#fullstack", "#react", "#css"),
            List.of(
                "CSS grid saved me hours today. Still don't fully understand it. 🕸️ #css #frontend",
                "Built a full-stack side project in a weekend — here's the tech stack 🛠️ #fullstack #webDev",
                "React 19 features that actually excite me in production 🚀 #react #webDev",
                "Spending 10 hours on a bug caused by a missing semicolon 😤 #frontend #webDev",
                "API rate-limiting: the unglamorous feature that saves everything at scale ⚡ #fullstack"
            ), PostType.FEED),

        new ThemeBucket("cybersecurity",
            List.of("#cybersecurity", "#hacking", "#infosec", "#pentest", "#privacy"),
            List.of(
                "Phishing simulation results: 40% click rate in the first attempt 😬 #cybersecurity #infosec",
                "CTF challenge writeup: how we got root in 47 minutes ⚔️ #pentest #hacking",
                "Your password manager is the most important app you're not using 🔐 #privacy #cybersecurity",
                "Zero-day disclosure timeline and why responsible disclosure matters 📜 #infosec",
                "Network traffic anomaly that turned out to be a very bored intern 🤦 #cybersecurity #pentest"
            ), PostType.FEED),

        new ThemeBucket("mobileApps",
            List.of("#mobileApps", "#ios", "#android", "#ux", "#appDev"),
            List.of(
                "UX teardown: why this onboarding flow converts 3x better than average 📱 #ux #mobileApps",
                "iOS vs Android debate my team has every sprint 🤔 #ios #android #appDev",
                "Shipped a zero-crash release. It has been a good sprint 🚀 #mobileApps #appDev",
                "App Store review that made my morning: 'it shows not being dumb' ⭐ #mobileApps #ux",
                "SwiftUI animation that took 20 lines instead of the 200 I expected 🎉 #ios #appDev"
            ), PostType.FEED),

        new ThemeBucket("wineTasting",
            List.of("#wine", "#wineTasting", "#sommelier", "#naturalWine", "#wineClub"),
            List.of(
                "Natural wine tasting night: 6 bottles, 0 headaches, all opinions 🍷 #naturalWine #wine",
                "Somm tip: the second cheapest bottle on a restaurant list is almost always the best value 💡 #sommelier #wineTasting",
                "Pairing this Burgundy with cheese is basically a religious experience 🧀 #wine #wineClub",
                "Blind tasting result: I am apparently better at this when I stop overthinking 🎯 #wineTasting",
                "Vineyard visit: where the wine is made is as interesting as the wine itself 🌿 #wine #naturalWine"
            ), PostType.FEED),

        new ThemeBucket("veganism",
            List.of("#vegan", "#plantBased", "#veganFood", "#animalRights", "#veganBaking"),
            List.of(
                "Plant-based burger that genuinely fooled my very skeptical family 🍔 #vegan #plantBased",
                "7 protein sources every vegan should rotate for complete amino acids 💪 #vegan #veganFood",
                "Cashew cheese wheel that took 48 hours and zero dairy 🧀 #veganBaking #plantBased",
                "One year fully plant-based: bloodwork and energy levels thread 🩸 #vegan #animalRights",
                "Veganising my grandmother's recipe felt both rebellious and respectful 👵🌱 #veganFood #vegan"
            ), PostType.FEED)
    );

    // ── Dependencies ──────────────────────────────────────────────────────────

    AuthServiceClient authServiceClient;
    PostRecommendationClient postRecommendationClient;
    UserServiceClient userServiceClient;
    PostRepository postRepository;
    PostEventPublisher postEventPublisher;
    CommentRepository commentRepository;
    ReactionRepository reactionRepository;
    UserInteractionRepository userInteractionRepository;
    HashtagRepository hashtagRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> seedEverything() {
        log.info("🚀 Starting full seed pipeline: users → interests → {} posts", TARGET_POSTS + SHARE_POST_TARGET);

        List<AccountSummaryResponse> accounts = fetchAccounts(MAX_ACCOUNTS);
        if (accounts.isEmpty()) {
            log.warn("⚠️  No accounts found in auth-service — full seeding skipped");
            return buildSummary(0, 0, 0, 0, 0, "No accounts available in auth-service");
        }

        Map<String, String> accountIdToUserId = resolveUserIds(accounts);
        List<String> userIds = accountIdToUserId.values().stream().toList();
        if (userIds.isEmpty()) {
            log.warn("⚠️  No user IDs could be resolved — full seeding skipped");
            return buildSummary(0, 0, 0, 0, 0, "No user IDs available in user-service");
        }

        purgeSocialFeedData();

        Random random = new Random();
        ReactionType[] reactionTypes = ReactionType.values();

        // ── Step 1: Seed interests ────────────────────────────────────────────
        log.info("📡 Step 1/3 — Seeding interests for {} users", userIds.size());
        int interestsPublished = seedInterests(accounts, accountIdToUserId, random);
        log.info("✅ Step 1/3 done — Seeded interests for {} users", interestsPublished);

        // ── Step 2: Build and save Posts ──────────────────────────────────────
        log.info("📝 Step 2/3 — Creating {} base posts + {} share posts", TARGET_POSTS, SHARE_POST_TARGET);
        List<Post> savedBasePosts = buildAndSaveBasePosts(userIds, random);
        List<Post> savedSharePosts = buildAndSaveSharePosts(savedBasePosts, userIds, random);
        List<Post> allSavedPosts = new ArrayList<>(savedBasePosts);
        allSavedPosts.addAll(savedSharePosts);
        log.info("✅ Step 2/3 done — Saved {} posts total", allSavedPosts.size());

        int publishedEvents = publishPostCreatedEvents(allSavedPosts);
        log.info("  └─ Published {} POST_CREATED Kafka events", publishedEvents);

        int seededHashtags = seedHashtags(allSavedPosts);
        log.info("  └─ Inserted {} new hashtag documents", seededHashtags);

        // ── Step 3: Comments, reactions, interactions ─────────────────────────
        log.info("💬 Step 3/3 — Creating engagement data");
        int[] engagementCounts = buildAndSaveEngagement(allSavedPosts, userIds, random, reactionTypes);
        log.info("✅ Step 3/3 done — {} comments, {} reactions, {} interactions",
                engagementCounts[0], engagementCounts[1], engagementCounts[2]);

        log.info("🏁 Full seed pipeline completed! {} posts total", allSavedPosts.size());
        String message = "Full seed done! Users: %d, Interests: %d, Posts: %d, Comments: %d, Reactions: %d, Interactions: %d"
                .formatted(userIds.size(), interestsPublished, allSavedPosts.size(),
                        engagementCounts[0], engagementCounts[1], engagementCounts[2]);
        return buildSummary(allSavedPosts.size(), engagementCounts[0], engagementCounts[1], engagementCounts[2], interestsPublished, message);
    }

    private void purgeSocialFeedData() {
        long postCount = postRepository.count();
        long commentCount = commentRepository.count();
        long reactionCount = reactionRepository.count();
        long interactionCount = userInteractionRepository.count();
        long hashtagCount = hashtagRepository.count();

        long total = postCount + commentCount + reactionCount + interactionCount + hashtagCount;
        if (total == 0) {
            log.info("🧹 No existing social-feed data found. Starting clean seed.");
            return;
        }

        log.info(
                "🧹 Clearing social-feed data before reseed: posts={}, comments={}, reactions={}, interactions={}, hashtags={}",
                postCount, commentCount, reactionCount, interactionCount, hashtagCount
        );

        // Delete child collections first, then posts.
        userInteractionRepository.deleteAll();
        reactionRepository.deleteAll();
        commentRepository.deleteAll();
        hashtagRepository.deleteAll();
        postRepository.deleteAll();

        log.info("✅ Social-feed data cleared. Proceeding with reseed.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int seedInterests(List<AccountSummaryResponse> accounts,
                              Map<String, String> accountIdToUserId,
                              Random random) {
        int count = 0;
        for (AccountSummaryResponse account : accounts) {
            try {
                // Pick interests that align with themes so user vectors cluster properly
                Set<String> interests = pickThemedInterests(random);
                userServiceClient.updateUserInterestsForSeed(account.id(), new UserInterestSeedUpdateRequest(interests));

                String userId = accountIdToUserId.get(account.id());
                if (userId != null && !userId.isBlank()) {
                    try {
                        postRecommendationClient.seedUserInterests(userId, new UserInterestSeedUpdateRequest(interests));
                    } catch (Exception ex) {
                        log.warn("⚠️  Re-vectorization failed for userId={}: {}", userId, ex.getMessage());
                    }
                }
                count++;
            } catch (Exception e) {
                log.error("❌ Failed to seed interests for accountId={}", account.id(), e);
            }
        }
        return count;
    }

    private List<Post> buildAndSaveBasePosts(List<String> userIds, Random random) {
        List<Post> batch = new ArrayList<>(BATCH_SIZE);
        List<Post> all   = new ArrayList<>(TARGET_POSTS);

        for (int i = 0; i < TARGET_POSTS; i++) {
            PostType type = pickWeightedPostType(random);
            ThemeBucket theme = THEMES.get(i % THEMES.size()); // cycle through themes evenly
            boolean trending = random.nextDouble() < TRENDING_FRACTION;
            batch.add(buildPost(type, theme, userIds, random, trending));

            if (batch.size() == BATCH_SIZE) {
                all.addAll(postRepository.saveAll(batch));
                log.debug("  ↳ Saved batch, total so far: {}", all.size());
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            all.addAll(postRepository.saveAll(batch));
        }
        return all;
    }

    private List<Post> buildAndSaveSharePosts(List<Post> sourcePool, List<String> userIds, Random random) {
        List<Post> batch = new ArrayList<>(BATCH_SIZE);
        List<Post> all   = new ArrayList<>(SHARE_POST_TARGET);

        for (int i = 0; i < SHARE_POST_TARGET; i++) {
            Post source = sourcePool.get(random.nextInt(sourcePool.size()));
            batch.add(buildSharePost(source, userIds, random));

            if (batch.size() == BATCH_SIZE) {
                all.addAll(postRepository.saveAll(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            all.addAll(postRepository.saveAll(batch));
        }
        return all;
    }

    /** @return [comentCount, reactionCount, interactionCount] */
    private int[] buildAndSaveEngagement(List<Post> posts, List<String> userIds,
                                          Random random, ReactionType[] reactionTypes) {
        List<Comment>         comments     = new ArrayList<>();
        List<Reaction>        reactions    = new ArrayList<>();
        List<UserInteraction> interactions = new ArrayList<>();

        for (Post post : posts) {
            boolean trending = post.getStats() != null
                    && post.getStats().getReactionCount() > REACTIONS_PER_POST * TRENDING_REACTION_MULTIPLIER / 2;

            int commentCount = trending ? COMMENTS_PER_POST * 5 : COMMENTS_PER_POST;
            int reactionCount = trending ? REACTIONS_PER_POST * TRENDING_REACTION_MULTIPLIER : REACTIONS_PER_POST;
            int interactionCount = trending ? INTERACTIONS_PER_POST * TRENDING_VIEW_MULTIPLIER : INTERACTIONS_PER_POST;

            for (int c = 0; c < commentCount; c++) {
                comments.add(Comment.builder()
                        .postId(post.getId())
                        .authorId(userIds.get(random.nextInt(userIds.size())))
                        .content(COMMENT_TEXTS[random.nextInt(COMMENT_TEXTS.length)])
                        .build());
            }

            List<String> shuffled = new ArrayList<>(userIds);
            Collections.shuffle(shuffled, random);
            for (String reactorId : shuffled.subList(0, Math.min(reactionCount, shuffled.size()))) {
                reactions.add(Reaction.builder()
                        .authorId(reactorId)
                        .targetId(post.getId())
                        .targetType(ReactionTargetType.POST)
                        .type(reactionTypes[random.nextInt(reactionTypes.length)])
                        .build());
            }

            for (int ii = 0; ii < interactionCount; ii++) {
                InteractionType iType = pickWeightedInteractionType(random);
                Instant createdAt = Instant.now().minusSeconds(random.nextInt(RECENCY_SPREAD_DAYS * 24 * 3600));
                interactions.add(UserInteraction.builder()
                        .userId(userIds.get(random.nextInt(userIds.size())))
                        .postId(post.getId())
                        .interactionType(iType)
                        .weight(iType.getWeight())
                        .createdAt(createdAt)
                        .ingestedAt(Instant.now())
                        .build());
            }
        }

        // Save in chunks
        List<Comment> savedComments = saveBatched(comments, BATCH_SIZE * 5,
                chunk -> commentRepository.saveAll(chunk));
        List<Reaction> savedReactions = saveBatched(reactions, BATCH_SIZE * 5,
                chunk -> reactionRepository.saveAll(chunk));
        saveBatched(interactions, BATCH_SIZE * 5,
                chunk -> userInteractionRepository.saveAll(chunk));

        applySeedReactionStats(posts, savedReactions);
        return new int[]{ savedComments.size(), savedReactions.size(), interactions.size() };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post builders
    // ─────────────────────────────────────────────────────────────────────────

    private Post buildPost(PostType type, ThemeBucket theme,
                            List<String> userIds, Random random, boolean trending) {

        String authorId = userIds.get(random.nextInt(userIds.size()));
        String caption  = theme.captions().get(random.nextInt(theme.captions().size()));

        // Spread over last RECENCY_SPREAD_DAYS days
        LocalDateTime uploadedAt = LocalDateTime.now()
                .minusSeconds(random.nextInt(RECENCY_SPREAD_DAYS * 24 * 3600));

        int baseReactions = trending
                ? REACTIONS_PER_POST * TRENDING_REACTION_MULTIPLIER
                : REACTIONS_PER_POST;
        int baseViews = trending
                ? REACTIONS_PER_POST * TRENDING_VIEW_MULTIPLIER
                : REACTIONS_PER_POST * 3;

        Post.PostBuilder<?, ?> builder = Post.builder()
                .authorId(authorId)
                .postType(type)
                .visibility(Visibility.ALL)
                .content(PostContent.builder()
                        .caption(caption)
                        .hashtags(pickSubset(theme.hashtags(), 2 + random.nextInt(2), random))
                        .build())
                .media(buildMedia(type, random))
                .stats(PostStats.builder()
                        .reactionCount(baseReactions)
                        .commentCount(COMMENTS_PER_POST)
                        .shareCount(random.nextInt(10))
                        .viewCount(baseViews)
                        .build())
                .uploadedAt(uploadedAt)
                .updatedAt(uploadedAt);

        if (type == PostType.STORY) {
            builder.expiresAt(uploadedAt.plusHours(24));
        }

        if (type == PostType.REEL || type == PostType.STORY) {
            builder.music(buildSeedMusic(random));
        }

        return builder.build();
    }

    private Post buildSharePost(Post source, List<String> userIds, Random random) {
        String shareAuthorId = userIds.get(random.nextInt(userIds.size()));
        LocalDateTime uploadedAt = LocalDateTime.now()
                .minusSeconds(random.nextInt(RECENCY_SPREAD_DAYS * 24 * 3600));

        // Inherit theme hashtags from source for semantic continuity
        List<String> inheritedHashtags = (source.getContent() != null && source.getContent().getHashtags() != null)
                ? source.getContent().getHashtags()
                : List.of();

        return Post.builder()
                .authorId(shareAuthorId)
                .postType(PostType.SHARE)
                .visibility(Visibility.ALL)
                .sharedPostId(source.getId())
                .originalAuthorId(source.getOriginalAuthorId() != null ? source.getOriginalAuthorId() : source.getAuthorId())
                .rootPostId(source.getRootPostId() != null ? source.getRootPostId() : source.getId())
                .sharedCaption(PostContent.builder()
                        .caption("Worth sharing 🔁 " + (source.getContent() != null ? source.getContent().getCaption() : ""))
                        .hashtags(inheritedHashtags)
                        .build())
                .stats(PostStats.builder()
                        .reactionCount(REACTIONS_PER_POST)
                        .commentCount(COMMENTS_PER_POST)
                        .shareCount(0)
                        .build())
                .uploadedAt(uploadedAt)
                .updatedAt(uploadedAt)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Media builders
    // ─────────────────────────────────────────────────────────────────────────

    private List<PostMedia> buildMedia(PostType type, Random random) {
        return switch (type) {
            case REEL -> List.of(PostMedia.builder()
                    .url(VIDEO_URLS[random.nextInt(VIDEO_URLS.length)])
                    .type("VIDEO").build());
            case STORY -> random.nextBoolean()
                    ? List.of(PostMedia.builder().url(VIDEO_URLS[random.nextInt(VIDEO_URLS.length)]).type("VIDEO").build())
                    : List.of(PostMedia.builder().url(IMAGE_URLS[random.nextInt(IMAGE_URLS.length)]).type("IMAGE").build());
            default -> {
                List<PostMedia> media = new ArrayList<>();
                int count = 1 + random.nextInt(3);
                List<String> pool = new ArrayList<>(List.of(IMAGE_URLS));
                Collections.shuffle(pool, random);
                for (int i = 0; i < Math.min(count, pool.size()); i++) {
                    media.add(PostMedia.builder().url(pool.get(i)).type("IMAGE").build());
                }
                yield media;
            }
        };
    }

    private PostMusic buildSeedMusic(Random random) {
        int idx = 1 + random.nextInt(50);
        return PostMusic.builder()
                .jamendoId("seed-" + idx)
                .title("Seed Track " + idx)
                .artistName("Seed Artist " + (1 + random.nextInt(10)))
                .audioUrl("https://example.com/audio/" + idx + ".mp3")
                .coverUrl("https://picsum.photos/seed/music" + idx + "/100/100")
                .duration(120 + random.nextInt(180))
                .albumName("Seed Album " + (1 + random.nextInt(5)))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Weighted randomisation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Weighted distribution: FEED=55%, REEL=20%, STORY=5%,
     * remaining 20% will become SHARE posts in step 2.
     */
    private PostType pickWeightedPostType(Random random) {
        int roll = random.nextInt(100);
        if (roll < FEED_FRACTION)  return PostType.FEED;
        if (roll < FEED_FRACTION + REEL_FRACTION) return PostType.REEL;
        if (roll < FEED_FRACTION + REEL_FRACTION + STORY_FRACTION) return PostType.STORY;
        return PostType.FEED; // remainder also goes to FEED (SHARE built separately)
    }

    /**
     * Realistic interaction-type weights: VIEW >> LIKE > COMMENT > SHARE > DISLIKE.
     * This mimics real engagement funnels so the RRF engine's interaction-weighted
     * user vectors encode meaningful signal.
     */
    private InteractionType pickWeightedInteractionType(Random random) {
        int roll = random.nextInt(100);
        if (roll < 70) return InteractionType.VIEW;
        if (roll < 85) return InteractionType.LIKE;
        if (roll < 93) return InteractionType.COMMENT;
        if (roll < 97) return InteractionType.SHARE;
        return InteractionType.DISLIKE;
    }

    /**
     * Pick interests aligned with theme names so user interest vectors
     * cluster around the same semantic space as post hashtags.
     */
    private Set<String> pickThemedInterests(Random random) {
        List<String> themeNames = THEMES.stream().map(ThemeBucket::theme).toList();
        List<String> pool = new ArrayList<>(themeNames);
        Collections.shuffle(pool, random);
        return new LinkedHashSet<>(pool.subList(0, Math.min(INTERESTS_PER_USER, pool.size())));
    }

    private List<String> pickSubset(List<String> source, int count, Random random) {
        List<String> pool = new ArrayList<>(source);
        Collections.shuffle(pool, random);
        return pool.subList(0, Math.min(count, pool.size()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hashtag seeding
    // ─────────────────────────────────────────────────────────────────────────

    private int seedHashtags(List<Post> posts) {
        Set<String> normalized = new LinkedHashSet<>();
        for (Post post : posts) {
            collectHashtags(post.getContent(), normalized);
            collectHashtags(post.getSharedCaption(), normalized);
        }

        int inserted = 0;
        for (String tag : normalized) {
            try {
                if (!hashtagRepository.existsByNormalizedValue(tag)) {
                    hashtagRepository.save(Hashtag.builder()
                            .value("#" + tag)
                            .normalizedValue(tag)
                            .build());
                    inserted++;
                }
            } catch (Exception e) {
                log.warn("⚠️  Failed to seed hashtag '{}': {}", tag, e.getMessage());
            }
        }
        return inserted;
    }

    private void collectHashtags(PostContent content, Set<String> target) {
        if (content == null || content.getHashtags() == null) return;
        content.getHashtags().forEach(tag ->
                target.add(tag.trim().replaceFirst("^#+", "").toLowerCase()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reaction stats
    // ─────────────────────────────────────────────────────────────────────────

    private void applySeedReactionStats(List<Post> posts, List<Reaction> reactions) {
        Map<String, Map<ReactionType, Long>> countsByPost = new HashMap<>();
        for (Reaction r : reactions) {
            if (r.getTargetType() != ReactionTargetType.POST || !r.isActive()) continue;
            countsByPost.computeIfAbsent(r.getTargetId(), k -> new EnumMap<>(ReactionType.class))
                        .merge(r.getType(), 1L, Long::sum);
        }

        List<Post> toUpdate = new ArrayList<>(BATCH_SIZE);
        for (Post post : posts) {
            Map<ReactionType, Long> counts = countsByPost.getOrDefault(post.getId(), Map.of());
            int total = counts.values().stream().mapToInt(Long::intValue).sum();
            List<ReactionType> topReactions = counts.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<ReactionType, Long>, Long>comparing(Map.Entry::getValue).reversed())
                    .limit(3).map(Map.Entry::getKey).toList();

            PostStats stats = post.getStats() == null ? PostStats.builder().build() : post.getStats();
            stats.setReactionCount(total);
            stats.setTopReactions(topReactions);
            post.setStats(stats);
            post.setUpdatedAt(LocalDateTime.now());
            toUpdate.add(post);

            if (toUpdate.size() == BATCH_SIZE) {
                postRepository.saveAll(toUpdate);
                toUpdate.clear();
            }
        }
        if (!toUpdate.isEmpty()) {
            postRepository.saveAll(toUpdate);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kafka
    // ─────────────────────────────────────────────────────────────────────────

    private int publishPostCreatedEvents(List<Post> posts) {
        int published = 0;
        for (Post post : posts) {
            try {
                postEventPublisher.publishPostCreated(post);
                published++;
            } catch (Exception e) {
                log.warn("⚠️ Failed to publish POST_CREATED for postId={}", post.getId(), e);
            }
        }
        return published;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Infrastructure / clients
    // ─────────────────────────────────────────────────────────────────────────

    private List<AccountSummaryResponse> fetchAccounts(int limit) {
        ApiResponse<List<AccountSummaryResponse>> response = authServiceClient.getAllAccounts();
        if (response == null || response.data() == null) return List.of();
        List<AccountSummaryResponse> all = response.data();
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    private Map<String, String> resolveUserIds(List<AccountSummaryResponse> accounts) {
        Map<String, String> result = new HashMap<>();
        for (AccountSummaryResponse account : accounts) {
            try {
                ApiResponse<UserSummaryResponse> response = userServiceClient.getUserSummaryByAccountId(account.id());
                String userId = (response != null && response.data() != null) ? response.data().id() : null;
                if (userId != null && !userId.isBlank()) {
                    result.put(account.id(), userId);
                } else {
                    log.warn("⚠️  Missing userId for accountId={}", account.id());
                }
            } catch (Exception ex) {
                log.warn("⚠️  Failed to resolve userId for accountId={}: {}", account.id(), ex.getMessage());
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface BatchSaver<T> {
        List<T> save(List<T> chunk);
    }

    private <T> List<T> saveBatched(List<T> items, int chunkSize, BatchSaver<T> saver) {
        List<T> all = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i += chunkSize) {
            List<T> chunk = items.subList(i, Math.min(i + chunkSize, items.size()));
            all.addAll(saver.save(chunk));
        }
        return all;
    }

    private Map<String, Object> buildSummary(int posts, int comments, int reactions,
                                              int interactions, int interests, String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("posts", posts);
        r.put("comments", comments);
        r.put("reactions", reactions);
        r.put("interactions", interactions);
        r.put("interests_published", interests);
        r.put("message", message);
        return r;
    }
}
