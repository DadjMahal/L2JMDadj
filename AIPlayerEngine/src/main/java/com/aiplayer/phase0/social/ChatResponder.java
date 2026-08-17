package com.aiplayer.phase0.social;

/** MODE: PARTIAL. Reads from the not-yet-migrated GameStateMirror rather than BotSnapshot — see INTEGRATION_GAPS.md. */

import com.aiplayer.protocol.L2JProtocol;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Handles contextual responses to PMs, trade chat mentions, shouts, and party chat.
 * Uses pattern matching (no LLM) with personality-driven reply selection.
 *
 * Response categories:
 * - Greetings (hi, hello, hey)
 * - Trade requests (buy, sell, price, trade)
 * - Party requests (party, pt, invite)
 * - Help questions (where, how, what)
 * - Aggression (noob, stupid, trash)
 * - Random social (lol, nice, gj)
 * - AFK / BRB
 * - LFG (looking for group)
 */
public final class ChatResponder {

    private static final String[] GREETING_KEYWORDS = {"hi", "hello", "hey", "hola", "yo", "sup", "greetings", "welcome"};
    private static final String[] TRADE_KEYWORDS = {"buy", "sell", "price", "trade", "wts", "wtb", "shop", "adena", "cheap", "offer"};
    private static final String[] PARTY_KEYWORDS = {"party", "pt", "invite", "group", "lfg", "lfm", "need healer", "need tank"};
    private static final String[] HELP_KEYWORDS = {"where", "how", "what", "help", "quest", "location", "drop", "farm"};
    private static final String[] AGGRESSION_KEYWORDS = {"noob", "stupid", "trash", "idiot", "loser", "fk", "fck", "bot", "cheater", "hacker"};
    private static final String[] POSITIVE_KEYWORDS = {"lol", "nice", "gj", "good job", "wp", "well played", "gratz", "congrats", "ty", "thanks"};
    private static final String[] AFK_KEYWORDS = {"afk", "brb", "back", "away", "later", "gtg", "cya"};
    private static final String[] LEVELING_KEYWORDS = {"lvl", "level", "xp", "exp", "grind", "farming", "spot"};

    private final String accountName;
    private final L2JProtocol protocol;
    private final ChatPersonality personality;
    private final ChatHistory history;
    private final SocialTimer timer;
    private final Random rng;

    // Response templates per archetype and category
    private static final Map<ChatPersonality.Archetype, Map<String, String[]>> RESPONSE_BANK = new HashMap<>();

    static {
        buildResponseBank();
    }

    public ChatResponder(String accountName, L2JProtocol protocol,
                         ChatPersonality personality, ChatHistory history,
                         SocialTimer timer) {
        this.accountName = accountName;
        this.protocol = protocol;
        this.personality = personality;
        this.history = history;
        this.timer = timer;
        this.rng = personality.getRng();
    }

    /**
     * Process an incoming message and decide whether to reply.
     * Returns the reply text, or null if no reply.
     */
    public String onIncomingMessage(String sender, ChatMessage.Channel channel, String text) {
        if (text == null || text.trim().isEmpty()) return null;

        String lower = text.toLowerCase();

        // Do not reply to own messages
        if (sender.equalsIgnoreCase(accountName)) return null;

        // Rate limit check
        if (!canReply(channel)) return null;

        // Check if directly addressed
        boolean isAddressed = lower.contains(accountName.toLowerCase())
                || channel == ChatMessage.Channel.TELL;

        // Personality check: quiet types rarely reply to non-addressed messages
        if (!isAddressed && personality.archetype == ChatPersonality.Archetype.QUIET) {
            if (!personality.rollReply()) return null;
        }

        if (!isAddressed && !personality.rollTalkative()) {
            return null;
        }

        // Classify message
        String category = classifyMessage(lower);

        // Build response
        String response = buildResponse(category, sender, isAddressed, channel);
        if (response != null) {
            history.addIncoming(sender, channel, text);
            history.addOutgoing(channel, response);
            markReply(channel);
        }
        return response;
    }

    /**
     * Generate a proactive message (shout, trade chat, random social).
     * Returns message text or null.
     */
    public String generateProactive(ChatMessage.Channel channel) {
        if (!canProactive(channel)) return null;

        String msg = null;
        switch (channel) {
            case SHOUT:
                msg = generateShout();
                break;
            case TRADE:
                msg = generateTradeShout();
                break;
            case ALL:
                msg = generateRandomSocial();
                break;
            case PARTY:
                msg = generatePartyChat();
                break;
            default:
                break;
        }

        if (msg != null) {
            history.addOutgoing(channel, msg);
            markProactive(channel);
        }
        return msg;
    }

    public boolean shouldReplyToPartyInvite(String inviterName) {
        if (!timer.canRespondToPartyInvite()) return false;

        switch (personality.archetype) {
            case FRIENDLY:
            case NEWBIE:
            case LEADER:
                return true;
            case QUIET:
            case GRINDER:
                return rng.nextDouble() < 0.3;
            case TROLL:
                return rng.nextDouble() < 0.5;
            default:
                return rng.nextDouble() < 0.6;
        }
    }

    public boolean shouldSendPartyInvite() {
        return timer.canPartyInvite() && personality.rollSocialAggression();
    }

    // ------------------------------------------------------------------
    // Private implementation
    // ------------------------------------------------------------------

    private String classifyMessage(String lower) {
        if (containsAny(lower, GREETING_KEYWORDS)) return "greeting";
        if (containsAny(lower, TRADE_KEYWORDS)) return "trade";
        if (containsAny(lower, PARTY_KEYWORDS)) return "party";
        if (containsAny(lower, HELP_KEYWORDS)) return "help";
        if (containsAny(lower, AGGRESSION_KEYWORDS)) return "aggression";
        if (containsAny(lower, POSITIVE_KEYWORDS)) return "positive";
        if (containsAny(lower, AFK_KEYWORDS)) return "afk";
        if (containsAny(lower, LEVELING_KEYWORDS)) return "leveling";
        return "generic";
    }

    private String buildResponse(String category, String sender,
                                  boolean isAddressed, ChatMessage.Channel channel) {
        String[] templates = getTemplates(category);
        if (templates == null || templates.length == 0) return null;

        String template = templates[rng.nextInt(templates.length)];

        // Substitute placeholders
        String response = template.replace("{name}", sender.split(" ")[0]);

        // Apply personality formatting
        response = personality.formatMessage(response);

        // Add archetype-specific flourishes
        if (personality.usesEmotes && rng.nextDouble() < 0.15) {
            String[] emotes = {" :D", " :)", " ;)"};
            response += emotes[rng.nextInt(emotes.length)];
        }

        return response;
    }

    private String generateShout() {
        String[] shouts = {
            "Anyone farming Cruma?",
            "Looking for party at Execution Grounds",
            "Nice weather for grinding today",
            "Where is everyone?",
            "Good luck on drops!",
            "Anyone seen the RB?",
            "Nice spot, mind if I join?",
            "Selling mats, PM me"
        };
        return shouts[rng.nextInt(shouts.length)];
    }

    private String generateTradeShout() {
        String[] tradeShouts = {
            "WTS mats, PM offers",
            "WTB soulshots, any grade",
            "Selling recipes cheap",
            "Anyone selling D grade gear?",
            "Buying herbs, good price",
            "WTS full drops, PM me"
        };
        return tradeShouts[rng.nextInt(tradeShouts.length)];
    }

    private String generateRandomSocial() {
        String[] social = {
            "lol", "nice", "gj everyone", "that was close",
            "brb", "back", "anyone need help?", "what a drop!", "gratz"
        };
        return social[rng.nextInt(social.length)];
    }

    private String generatePartyChat() {
        String[] party = {
            "Pulling next mob", "Buffs up", "Careful, aggro",
            "Nice pull", "Healing up", "Ready when you are",
            "Going OOM soon"
        };
        return party[rng.nextInt(party.length)];
    }

    private boolean canReply(ChatMessage.Channel channel) {
        switch (channel) {
            case TELL: return timer.canPmReply();
            case TRADE: return timer.canTradeChat();
            case SHOUT: return timer.canShout();
            case PARTY: return timer.canGlobalChat();
            case ALL: return timer.canGlobalChat();
            default: return true;
        }
    }

    private boolean canProactive(ChatMessage.Channel channel) {
        switch (channel) {
            case SHOUT: return timer.canShout();
            case TRADE: return timer.canTradeChat();
            case ALL: return timer.canGlobalChat();
            case PARTY: return timer.canGlobalChat();
            default: return false;
        }
    }

    private void markReply(ChatMessage.Channel channel) {
        switch (channel) {
            case TELL: timer.markPmReply(); break;
            case TRADE: timer.markTradeChat(); break;
            case SHOUT: timer.markShout(); break;
            case PARTY: timer.markGlobalChat(); break;
            case ALL: timer.markGlobalChat(); break;
            default: break;
        }
    }

    private void markProactive(ChatMessage.Channel channel) {
        switch (channel) {
            case SHOUT: timer.markShout(); break;
            case TRADE: timer.markTradeChat(); break;
            case ALL: timer.markGlobalChat(); break;
            case PARTY: timer.markGlobalChat(); break;
            default: break;
        }
    }

    private String[] getTemplates(String category) {
        Map<String, String[]> archetypeResponses = RESPONSE_BANK.get(personality.archetype);
        if (archetypeResponses == null) {
            return RESPONSE_BANK.get(ChatPersonality.Archetype.FRIENDLY).get(category);
        }
        return archetypeResponses.get(category);
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Response bank builder
    // ------------------------------------------------------------------

    private static void buildResponseBank() {
        // FRIENDLY
        RESPONSE_BANK.put(ChatPersonality.Archetype.FRIENDLY, new HashMap<>(){{
            put("greeting", new String[]{
                "Hey {name}!", "Hello there :D", "Hi {name}, how is it going?", "Heyo!"
            });
            put("trade", new String[]{
                "I might have some, let me check", "What are you looking for?",
                "Sure, PM me", "I am not selling right now, sorry"
            });
            put("party", new String[]{
                "Sure, invite me!", "I would love to join",
                "Give me a sec, almost done here", "Sorry, solo grinding right now"
            });
            put("help", new String[]{
                "I think it is around there, not sure tho",
                "Check the wiki maybe?", "I can help, where are you?",
                "Hmm, I do not know either"
            });
            put("aggression", new String[]{
                "Chill dude, it is just a game", "No need to be rude",
                "Sorry if I bothered you", "Let us just play"
            });
            put("positive", new String[]{
                "Thanks!", "You too!", "Appreciate it {name}", "lol yeah"
            });
            put("afk", new String[]{
                "kk, take your time", "brb too maybe", "alright, cya"
            });
            put("leveling", new String[]{
                "I am at {name}s spot, pretty good xp",
                "Cruma is decent for your level", "Try Ant Nest maybe?",
                "Just grinding here"
            });
            put("generic", new String[]{"Yeah", "True", "Same", "lol"});
        }});

        // TROLL
        RESPONSE_BANK.put(ChatPersonality.Archetype.TROLL, new HashMap<>(){{
            put("greeting", new String[]{
                "Oh look who it is", "Sup noob", "Hey {name}, still alive?"
            });
            put("trade", new String[]{
                "Everything is overpriced, deal with it",
                "My prices are fair, unlike your gear",
                "Make me an offer I cannot refuse"
            });
            put("party", new String[]{
                "Only if you can keep up", "Carry me and I will join",
                "Nah, you would slow me down"
            });
            put("help", new String[]{
                "Google it", "It is called a map, use it",
                "Why should I tell you?", "Figure it out yourself"
            });
            put("aggression", new String[]{
                "Mad cuz bad", "Cry more", "You first", "Report me, I dare you"
            });
            put("positive", new String[]{
                "Obviously", "Took you long enough", "Not impressed"
            });
            put("afk", new String[]{
                "Do not come back", "Finally some peace", "k"
            });
            put("leveling", new String[]{
                "Git gud and solo it", "Party is for weaklings",
                "I am already max level, obviously"
            });
            put("generic", new String[]{"k", "whatever", "sure buddy", "lol no"});
        }});

        // QUIET
        RESPONSE_BANK.put(ChatPersonality.Archetype.QUIET, new HashMap<>(){{
            put("greeting", new String[]{"Hey", "Hi", "."});
            put("trade", new String[]{"No", "Maybe later", "..."});
            put("party", new String[]{"No thanks", "Solo", "."});
            put("help", new String[]{"Dunno", "Sorry", "..."});
            put("aggression", new String[]{"...", "Ignore list", "k"});
            put("positive", new String[]{"ty", "np", "."});
            put("afk", new String[]{"k", "cya", "."});
            put("leveling", new String[]{"Here", "Solo", "..."});
            put("generic", new String[]{"k", "yeah", "."});
        }});

        // TRADER
        RESPONSE_BANK.put(ChatPersonality.Archetype.TRADER, new HashMap<>(){{
            put("greeting", new String[]{
                "Hey {name}, buying or selling?", "Hello, interested in trades?", "Hi! Got anything good?"
            });
            put("trade", new String[]{
                "I have plenty, what do you need?", "Good prices guaranteed",
                "PM me your offer", "I can craft that too"
            });
            put("party", new String[]{
                "If it helps me farm mats, sure", "Party for RB drops?",
                "Only if we split loot fair"
            });
            put("help", new String[]{
                "Check Giran market", "I sell maps too, 10k each",
                "Try the grocery NPC"
            });
            put("aggression", new String[]{
                "Keep talking, prices go up", "Your loss",
                "I have customers to attend to"
            });
            put("positive", new String[]{
                "Business is good!", "Thanks, come again",
                "Pleasure doing business"
            });
            put("afk", new String[]{
                "Back to trading soon", "Shop is closing for a bit",
                "brb, do not steal my customers"
            });
            put("leveling", new String[]{
                "Farm spots are free real estate",
                "Good drops = good adena", "I know all the profitable spots"
            });
            put("generic", new String[]{
                "Interesting", "Maybe", "Let us talk business"
            });
        }});

        // LEADER
        RESPONSE_BANK.put(ChatPersonality.Archetype.LEADER, new HashMap<>(){{
            put("greeting", new String[]{
                "Greetings {name}", "Hello soldier", "Welcome"
            });
            put("trade", new String[]{
                "Guild bank has supplies", "Ask the quartermaster",
                "We pool resources here"
            });
            put("party", new String[]{
                "Join up, we are pushing", "Form on me",
                "Need one more, join us", "Follow my lead"
            });
            put("help", new String[]{
                "I have marked the location", "Check strategy guide",
                "Ask in clan chat", "I will show you"
            });
            put("aggression", new String[]{
                "That is enough", "Watch your tone",
                "One more word and you are out", "Respect the chain of command"
            });
            put("positive", new String[]{
                "Good work", "Well done team",
                "That is how it is done", "Excellent"
            });
            put("afk", new String[]{
                "Hold the line while I am gone",
                "Back in 5, do not wipe", "Cover me"
            });
            put("leveling", new String[]{
                "We are farming as a group", "Stick to the plan",
                "Target priority: healers first"
            });
            put("generic", new String[]{
                "Understood", "Carry on", "Affirmative"
            });
        }});

        // NEWBIE
        RESPONSE_BANK.put(ChatPersonality.Archetype.NEWBIE, new HashMap<>(){{
            put("greeting", new String[]{
                "Hi! I am new here", "Hello everyone!",
                "Hey {name}, nice to meet you!"
            });
            put("trade", new String[]{
                "How much is that?", "I do not have much adena...",
                "Is that expensive?", "Can I trade later?"
            });
            put("party", new String[]{
                "Yes please! I need help",
                "Can someone show me the ropes?",
                "I would love to join!",
                "Are you sure I am not too low level?"
            });
            put("help", new String[]{
                "I am lost, where do I go?",
                "How does this work?",
                "What is the best weapon for me?",
                "Thank you so much!"
            });
            put("aggression", new String[]{
                "I am sorry!", "I did not mean to...",
                "Please do not be mad", "I am still learning"
            });
            put("positive", new String[]{
                "Wow thanks!", "You are so nice!",
                "I appreciate it!", "Yay!"
            });
            put("afk", new String[]{
                "Be right back!", "Sorry, phone call",
                "Back soon, do not leave me!"
            });
            put("leveling", new String[]{
                "Where should I grind?",
                "What level is this area?",
                "Am I too weak for here?", "This is hard..."
            });
            put("generic", new String[]{
                "Really?", "Oh I see!", "Cool!", "Thanks for explaining"
            });
        }});

        // GRINDER
        RESPONSE_BANK.put(ChatPersonality.Archetype.GRINDER, new HashMap<>(){{
            put("greeting", new String[]{
                "Hey, do not steal my mobs", "Hi, this spot is taken", "Hey"
            });
            put("trade", new String[]{
                "Only selling drops, no time to chat", "WTB shots only",
                "Too busy farming"
            });
            put("party", new String[]{
                "If you increase my xp/hr, sure", "Only if you pull fast",
                "Solo is more efficient"
            });
            put("help", new String[]{
                "Farm it yourself", "Check drop database",
                "I do not have time for this"
            });
            put("aggression", new String[]{
                "Move along", "You are wasting my time", "Not now", "..."
            });
            put("positive", new String[]{
                "Nice drop", "Good pull", "Efficient"
            });
            put("afk", new String[]{
                "Farming, brb never", "Back to grinding", "..."
            });
            put("leveling", new String[]{
                "This spot gives 500k xp/hr",
                "Do not bother with that mob", "Optimal route only"
            });
            put("generic", new String[]{"k", "busy", "later"});
        }});

        // ROLEPLAYER
        RESPONSE_BANK.put(ChatPersonality.Archetype.ROLEPLAYER, new HashMap<>(){{
            put("greeting", new String[]{
                "Hail, {name}!", "Well met, traveler",
                "Greetings from Aden", "The gods smile upon you"
            });
            put("trade", new String[]{
                "I have wares if you have coin",
                "The merchant guild offers fair prices",
                "My wares are blessed by the elders"
            });
            put("party", new String[]{
                "Join our fellowship!", "The more blades the merrier",
                "Together we shall prevail", "Stand with me, friend"
            });
            put("help", new String[]{
                "The ancient texts speak of this...",
                "I shall guide you, brave one",
                "Seek the oracle in the temple",
                "Legend says it lies to the east"
            });
            put("aggression", new String[]{
                "You dare insult me?",
                "My honor demands satisfaction!",
                "The gods frown upon your words", "Begone, knave!"
            });
            put("positive", new String[]{
                "Fortune favors us!", "A glorious victory!",
                "The bards shall sing of this", "Well struck!"
            });
            put("afk", new String[]{
                "I must tend to my steed",
                "The realm calls me away briefly",
                "I shall return posthaste"
            });
            put("leveling", new String[]{
                "We hunt the beasts of the forest",
                "Glory and experience await",
                "Sharpen your blade, adventure calls"
            });
            put("generic", new String[]{
                "Indeed", "For Aden!", "By Shilen grace", "Fascinating tale"
            });
        }});
    }
}
