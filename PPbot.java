import org.jibble.pircbot.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;
import java.io.*;

public class PPbot extends PircBot
{

    // for indexing into the mess below
    final static int T_NICK = 0;
    final static int T_EXACT = 1;
    final static int T_KEYWORD = 2;
    final static int T_VARIABLE = 3;
    final static int T_DELTA = 4;

    final String[][] triggers = {
//  {string nick,       bool exact, string match,   string variable,                 int delta}
    {"danyell",         "false",    "hah",          "danyell.says.hah",              "1"},
    {"BungoDanderfluff","true",     "meow",         "meow",                          "1"},
    {"xx3nvyxx",        "true",     "meow",         "meow",                          "1"},
    {"jtb",             "false",    "show",         "jonthebastard.mentions.a.show", "1"},
    {"jonthebastard",   "false",    "show",         "jonthebastard.mentions.a.show", "1"},
    {"jtb",             "false",    "shows",        "jonthebastard.mentions.a.show", "1"},
    {"jonthebastard",   "false",    "shows",        "jonthebastard.mentions.a.show", "1"},
    {"jtb",             "false",    "concert",      "jonthebastard.mentions.a.show", "1"},
    {"jonthebastard",   "false",    "concert",      "jonthebastard.mentions.a.show", "1"},
    {"jtb",             "false",    "concerts",     "jonthebastard.mentions.a.show", "1"},
    {"jonthebastard",   "false",    "concerts",     "jonthebastard.mentions.a.show", "1"},
    {"jtb",             "false",    "gig",          "jonthebastard.mentions.a.show", "1"},
    {"jonthebastard",   "false",    "gig",          "jonthebastard.mentions.a.show", "1"},
    {"jtb",             "false",    "gigs",         "jonthebastard.mentions.a.show", "1"},
    {"jonthebastard",   "false",    "gigs",         "jonthebastard.mentions.a.show", "1"},
    {"jtb",             "false",    "ticket",       "jonthebastard.almost.mentions.a.show", "1"},
    {"jonthebastard",   "false",    "ticket",       "jonthebastard.almost.mentions.a.show", "1"},
    {"jtb",             "false",    "tickets",      "jonthebastard.almost.mentions.a.show", "1"},
    {"jonthebastard",   "false",    "tickets",      "jonthebastard.almost.mentions.a.show", "1"},
    {"danyell",         "false",    "hah",          "danyell.says.hah",             "1"},
    {"beatsake",        "false",    "hotpot",       "beatsake.mentions.hot.pot",    "1"},
    {"beatsake",        "false",    "hot pot",      "beatsake.mentions.hot.pot",    "1"},
    {"beatsake",        "false",    "hot.pot",      "beatsake.mentions.hot.pot",    "1"},
    {"",                "false",    "cats--",       "cat.hater",                    "-10"},
    {"corioliss",       "false",    "ducks",        "corioliss.ducks",              "1"} };

    final String MAGIC_RESPONSE_CATEGORY = "magic8ball";
    final String[] blacklistUsers = {"dongbot"};
    final String[] blacklistKeys = {"gogurt"};

    final String[] FILTERED_STRINGS = {"saagar", "ted"};

    static final int MAX_MESSAGE_LEN = 400;

    static final String KEY_REGEX = "[\\[\\]\\w\\._\\-|\\{\\}]{2,}";

    class Parse
    {
        public String channel;
        public String sender;
        public String key;
        public long when;
    }

    class EntryComparator implements Comparator<Map.Entry<String, Integer> >
    {
        public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b)
        {
            Integer value1 = a.getValue();
            Integer value2 = b.getValue();

            if(value1.compareTo(value2) == 0)
            {
                String word1 = a.getKey();
                String word2 = b.getKey();

                return word1.compareToIgnoreCase(word2);

            } else
            {
                return value2.compareTo(value1);
            }
        }
    }

    static final long RECENT_WINDOW_MILLISECONDS = 30 * 60 * 1000; // 30 minutes

    Hashtable<String, Integer> values = new Hashtable<String, Integer>();
    Vector<Parse> recentParses = new Vector<Parse>();
    Hashtable<String, Vector<String> > links = new Hashtable<String, Vector<String> >();

    Hashtable<String, Vector<String> > facts = new Hashtable<String, Vector<String> >();

    Vector<Parse> pendingParseResults = new Vector<Parse>();
    Timer pendingResultsTimer;
    static final long PENDING_RESULTS_TIMER_MILLIS = 15 * 1000; // 15 seconds

    class Reminder
    {
        long created;
        long when;
        long when_expired;
        String sender ;
        String destination;
        String message;

        String channel;

        public String toString()
        {
            SimpleDateFormat fmt = new SimpleDateFormat("hh:mm aa 'at' MM/dd/yyyy ");

            String ret = "";
            ret += "on " + fmt.format(new Date(created)) + ", ";
            ret += sender + " asked me to remind you to ";
            ret += "\"" + message + "\" ";

            if(when != 0)
                ret += "at precisely " + fmt.format(new Date(when));
            else if (when_expired != 0)
                ret += ". I reminded you on " + fmt.format(new Date(when_expired)) + ", but you weren't active then. This is your final reminder.";

            return ret;
        }
    }
    static final long RECENT_ACTIVITY_MILLISECONDS = 60 * 1000; // 60 seconds
    static final long REMINDER_TIMER_PERIOD = 15 * 1000; // 15 seconds
    Hashtable<String, Long> activityTracker = new Hashtable<String, Long>();
    Hashtable<String, Vector<Reminder> > reminders = new Hashtable<String, Vector<Reminder> >();
    Timer reminderTimer;

    Vector<String> sunglassesWaitlist = new Vector<String>();

    class ReminderTask extends TimerTask
    {
        public void run()
        {
            checkTimedReminders();
        }
    };

    class PendingResultsTask extends TimerTask
    {
        public void run()
        {
            postPendingResults();
        }
    };

    String channel;
    String data_file;
    String data_file_backup;
    String link_file;
    String link_file_backup;
    String fact_file;
    String fact_file_backup;

    public PPbot(String channel, String name) {
        this.channel = channel;
        data_file = channel + ".dat";
        data_file_backup = channel + ".dat.bak";
        link_file = channel + ".link";
        link_file_backup = channel + ".link.bak";
        fact_file = channel + ".fact";
        fact_file_backup = channel + ".fact.bak";
    
        this.setAutoNickChange(true);
        this.setName(name);
        this.identify("5tr1p4m3");

        restoreData();

        onDisconnect();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
            String[] chans = getChannels();
            for(int i=0; i < chans.length; i++)
            {
                local_sendMessage(chans[i], line_header() + "oh shit, they're trying to shu$ d#wn $!@# )%()!#@%) !)!) 10928q$)(!@*$)moo");
                while(getOutgoingQueueSize() > 0)
                {
                    try { Thread.sleep(100); } catch (InterruptedException ie) {}
                }

                partChannel(chans[i], "ctrl-c, bitches");
            }
            }
        });

        reminderTimer = new Timer();
        reminderTimer.schedule(new ReminderTask(), 0, REMINDER_TIMER_PERIOD);
        pendingResultsTimer = new Timer();
    }

    public Vector<String> getMatches(String regex, String text)
    {
        Vector<String> retval = new Vector<String>();

        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        while(m.find())
        {
            retval.add(m.group(1).toLowerCase());
        }

        return retval;
    }

    public void blacklistCallOut(String sender, String key)
    {
        local_sendMessage("#"+channel, line_header() + " hey guys, guess who's a dick by trying to parse with " + key + "? oh, it's just " + sender + "--");
        applyMatch(getNick(), "#"+channel, sender, -1, false);
    }

    public void applyMatch(String sender, String channel, String key, int delta, boolean checkExpiry)
    {
        for(int i = 0; i < blacklistKeys.length; i++)
        {
            if(key.equalsIgnoreCase(blacklistKeys[i]))
            {
                local_sendMessage(sender, line_header() + "sorry, but " + key +" has been identified as a topic of great contention and been blacklisted");
                blacklistCallOut(sender, key);
                return;
            }
        }

        if(checkExpiry && (sender != getNick()))
        {
            // expire anything before expiry_millis
            long expiry_millis = ((new Date()).getTime() - RECENT_WINDOW_MILLISECONDS);

            // check the recent parses list to make sure it's not too soon
            Iterator<Parse> i = recentParses.iterator();
            while(i.hasNext())
            {
                Parse current = i.next();
                if(current.when < expiry_millis)
                {
                    System.out.println("parse for " + current.sender + " for " + current.key + " expired");
                    i.remove();
                } else if(current.sender.equalsIgnoreCase(sender) && current.key.equalsIgnoreCase(key))
                {
                    Date expiry = new Date(current.when - expiry_millis);
                    SimpleDateFormat format = new SimpleDateFormat("mm:ss", Locale.US);
                    format.setTimeZone(TimeZone.getTimeZone("GMT"));
                    local_sendMessage(sender, line_header() + "sorry, " + sender + ", but you can't change \"" + key + "\" for another " + format.format(expiry));
                    return;
                }
            }

            // it's valid! add it to recent parses
            Parse p = new Parse();
            p.sender = sender;
            p.key = key;
            p.when = (new Date()).getTime();
            recentParses.add(p);
        }

        if(!values.containsKey(key))
            values.put(key, new Integer(delta));
        else
            values.put(key, new Integer(values.get(key).intValue() + delta));

        if(channel.equalsIgnoreCase("#" + this.channel))
        {
            synchronized(pendingParseResults)
            {
                Parse p = new Parse();
                p.sender = sender;
                p.channel = channel;
                p.key = key;
                pendingParseResults.addElement(p);
                pendingResultsTimer.cancel();
                pendingResultsTimer = new Timer();
                pendingResultsTimer.schedule(new PendingResultsTask(), PENDING_RESULTS_TIMER_MILLIS);
            }
        } else
        {
            displayValue(channel, sender, key);
        }
    }

    // returns true if keys is not a unique set
    public boolean applyMatches(String sender, String channel, Vector<String> keys, int delta, boolean checkExpiry)
    {
        HashSet<String> tmp = new HashSet<String>(keys);
        for(String key : tmp)
        {
            applyMatch(sender, channel, key, delta, checkExpiry);
        }
    
        return (tmp.size() != keys.size());
    }

    public void processCommands(String channel, String sender, String message)
    {
        // process commands only if we're specifically targetted
        String commandHeader = getNick() + ":";
        if(message.toLowerCase().startsWith(commandHeader.toLowerCase()))
        {
            String command = message.substring(commandHeader.length()+1).trim().toLowerCase();
            String commandCase = message.substring(commandHeader.length()+1).trim();
            // top or '?' => default query
            System.out.println("command = " + command);

            if(command.equals("?") || command.equals("top"))
            {
                sendStatistics(channel, 5);

            } else if(command.startsWith("top")) //top N
            {
                String arg = command.substring(3).trim();
                try
                {
                    int n = Integer.parseInt(arg);
                    if(n > 25)
                        n = 25;
                    sendStatistics(channel, n);
                } catch(Exception e)
                {
                    local_sendMessage(sender, line_header() + "sorry, but I didn't understand the argument to that command!");
                    e.printStackTrace();
                }

            } else if(command.startsWith("??"))
            {
                String arg = command.substring(2).trim().toLowerCase();
                sendKeyedLinkStatistics(channel, sender, arg);

            } else if(command.startsWith("?"))
            {
                String arg = command.substring(1).trim().toLowerCase();
                sendKeyedStatistics(channel, sender, arg);

            } else if(command.contains("+="))
            {
                Vector<String> postUpdates = new Vector<String>();

                // parse out the two strings
                Pattern p = Pattern.compile("(" + KEY_REGEX + ")\\s*\\+=\\s*(" + KEY_REGEX + ")");
                Matcher m = p.matcher(message);
                match_add: while(m.find())
                {
                    System.out.println("linking " + m.group(1) + " to " + m.group(2));

                    String dest = m.group(1).toLowerCase();
                    String src = m.group(2).toLowerCase();

                    for(int i = 0; i < blacklistKeys.length; i++)
                    {
                        if(src.equalsIgnoreCase(blacklistKeys[i]))
                        {
                            local_sendMessage(sender, line_header() + "sorry, but " + src +" has been identified as a topic of great contention and been blacklisted");
                            blacklistCallOut(sender, src);
                            continue match_add;
                        }
                    }

                    if(dest.equals(src))
                    {
                            local_sendMessage(sender, line_header() + "sorry, but " + src + " can't be linked to itself!");
                            continue;
                    } else if(dest.equalsIgnoreCase(sender))
                    {
                            local_sendMessage(sender, line_header() + "sorry, but you can't link things to yourself!");
                            continue;
                    }

                    // make sure source exists
                    if(values.get(src) == null)
                        values.put(src, new Integer(0));

                    if(values.get(dest) == null)
                        values.put(dest, new Integer(0));

                    if(links.get(dest) == null)
                    {
                        Vector<String> targets = new Vector<String>();
                        targets.add(src);
                        links.put(dest, targets);
                    } else
                    {
                        Vector<String> targets = links.get(dest);

                        if(targets.contains(src))
                        {
                            local_sendMessage(sender, line_header() + "sorry, but " + src + " is already linked to " + dest);
                            continue;
                        }

                        targets.add(src);
                        links.put(dest, targets);
                    }

                    if(!postUpdates.contains(dest))
                        postUpdates.add(dest);

                    local_sendMessage(sender, line_header() + "I have linked the key \"" + dest + "\" so that it is now dependent on \"" + src + "\"!");
                }

                for(int i = 0; i < postUpdates.size(); i++)
                {
                    displayValue(sender, sender, postUpdates.elementAt(i));
                }

            } else if(command.contains("-="))
            {
                Vector<String> postUpdates = new Vector<String>();

                // parse out the two strings
                Pattern p = Pattern.compile("(" + KEY_REGEX + ")\\s*\\-=\\s*(" + KEY_REGEX + ")");
                Matcher m = p.matcher(message);
                while(m.find())
                {
                    System.out.println("unlinking " + m.group(1) + " from " + m.group(2));

                    String dest = m.group(1).toLowerCase();
                    String src = m.group(2).toLowerCase();

                    if(dest.equals(src))
                    {
                            local_sendMessage(sender, line_header() + "sorry, but " + src + " can't be linked to itself!");
                            continue;
                    } else if(dest.equalsIgnoreCase(sender))
                    {
                            local_sendMessage(sender, line_header() + "sorry, but you can't unlink things to yourself!");
                            continue;
                    }

                    if(links.get(dest) == null)
                    {
                        local_sendMessage(sender, line_header() + "sorry, but " + src + " isn't linked to " + dest);
                    } else
                    {
                        Vector<String> targets = links.get(dest);

                        if(targets.contains(src))
                        {
                            targets.remove(src);
                            links.put(dest, targets);
                        } else
                        {
                            local_sendMessage(sender, line_header() + "sorry, but " + src + " isn't linked to " + dest);
                            continue;
                        }
                    }

                    if(!postUpdates.contains(dest))
                        postUpdates.add(dest);

                    local_sendMessage(sender, line_header() + "I have unlinked the key \"" + dest + "\" so that it is no longer dependent on \"" + src + "\"!");
                }

                for(int i = 0; i < postUpdates.size(); i++)
                {
                    displayValue(sender, sender, postUpdates.elementAt(i));
                }

            } else if(command.startsWith("what the fuck is the score of") || command.startsWith("what the fuck is the value of"))
            {
                String arg = command.substring((new String("what the fuck is the score of")).length()).trim().toLowerCase();
                int delim = arg.indexOf("?");
                if(delim != -1)
                {
                    arg = arg.substring(0, delim);
                }
                sendKeyedStatistics(channel, sender, arg);

            } else if(command.startsWith("who the fuck cares about"))
            {
                String arg = command.substring((new String("who the fuck cares about")).length()).trim().toLowerCase();
                int delim = arg.indexOf("?");
                if(delim != -1)
                {
                    arg = arg.substring(0, delim);
                }
                sendKeyedLinkStatistics(channel, sender, arg);

            } else if(command.startsWith("remind"))
            {
                String patterns_dated_prefix[]  = {"remind (\\S+) at (.+) to (.+)",  "remind (\\S+) at (.+) that (.+)"};
                String patterns_dated_postfix[] = {"remind (\\S+) to (.+) at (.+)$", "remind (\\S+) that (.+) at (.+)"};
                String patterns_undated[]       = {"remind (\\S+) to (.+)",          "remind (\\S+) that (.+)"};

                Reminder reminder = null;
                try
                {
                    if(reminder == null)
                        reminder = parseReminder(patterns_dated_prefix, sender, command, 1, 2, 3);
                    if(reminder == null)
                        reminder = parseReminder(patterns_dated_postfix, sender, command, 1, 3, 2);
                    if(reminder == null)
                        reminder = parseReminder(patterns_undated, sender, command, 1, 0, 2);

                    if(reminder == null)
                    {
                        local_sendMessage(sender, line_header() + "sorry, but I couldn't parse your reminder. You should probably consult the manual (or bitch in IRC).");
                        return;
                    }

                    if(reminder.destination.equalsIgnoreCase(getNick()))
                    {
                        local_sendMessage(sender, line_header() + "Thanks, but I don't need reminding.");
                        return;
                    }

                    reminder.destination = reminder.destination.toLowerCase();

                    if(sender.equalsIgnoreCase(channel))
                        reminder.channel = reminder.destination;
                    else
                        reminder.channel = channel;

                    if((reminder.when == 0) && activityTracker.containsKey(reminder.destination))
                    {
                        Long timestamp = activityTracker.get(reminder.destination);
                        if(timestamp.longValue() > ((new Date()).getTime() - RECENT_ACTIVITY_MILLISECONDS))
                        {
                            local_sendMessage(sender, line_header() + "sorry, but " + reminder.destination + " has been active recently. Fucking tell them yourself like a grownup.");
                            return;
                        }
                    }

                    if((reminder.when != 0) && (reminder.when < (new Date()).getTime()))
                    {
                        local_sendMessage(sender, line_header() + "I can't remind people of things in the past yet. Feature pending invention of time travel.");
                        return;
                    }

                    synchronized(reminders)
                    {
                        // only one reminder per src,dst pair
                        if(!sender.equalsIgnoreCase(reminder.destination))
                        {
                            Vector<Reminder> destReminders = reminders.get(reminder.destination);
                            if(destReminders != null)
                            {
                                for(int i = 0; i < destReminders.size(); i++)
                                {
                                    Reminder r = destReminders.elementAt(i);
                                    if(r.sender.toLowerCase().contains(sender.toLowerCase()) || 
                                       sender.toLowerCase().contains(r.sender.toLowerCase()))
                                    {
                                        local_sendMessage(sender, line_header() + " you can only have one active reminder per person, so I am removing your previous reminder: " + r.toString());
                                        destReminders.removeElement(r);
                                        break;
                                    }
                                }

                                reminders.put(reminder.destination, destReminders);
                            }
                        }

                        Vector<Reminder> tmp = reminders.get(reminder.destination);
                        if(tmp == null)
                            tmp = new Vector<Reminder> ();
                        tmp.addElement(reminder);
                        reminders.put(reminder.destination, tmp);
                    }

                    String out = "okay, ";
                    if(reminder.when != 0)
                    {
                        SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyyy 'at' hh:mm aa");
                        out += "on or after " + fmt.format(new Date(reminder.when)) + ", ";
                    }
                    out += "I will remind " + reminder.destination + " of that when I see them. Keep in mind that reminders disappear if the bot crashes or is shut down, so don't rely on this for *super* important things.";
                    local_sendMessage(sender, line_header() + out);

                } catch(ParseException pe)
                {
                    local_sendMessage(sender, line_header() + "sorry, I couldn't parse your date!");
                    System.out.println(pe);
                }

               
            } else if(command.equalsIgnoreCase("rimshot"))
            {
                local_sendMessage(channel, line_header() + "ba-dum-tish!");
            } else if(command.equalsIgnoreCase("rimjob"))
            {
                local_sendMessage(channel, line_header() + "ba-dum-tush!");
            } else if(command.equalsIgnoreCase("date"))
            {
                local_sendMessage(channel, line_header() + "yo, it's " + (new Date().toString()));
            } else if(command.endsWith("..."))
            {
                sendAction(channel, "puts on sunglasses");
                if(!sunglassesWaitlist.contains(sender.toLowerCase()))
                    sunglassesWaitlist.addElement(sender.toLowerCase());
            } else if(command.startsWith("facts about ") || command.startsWith("facts."))
            {
                String topic = "";
                // subtopic
                if(command.startsWith("facts about "))
                {
                    topic = command.substring(command.indexOf("facts about ") + ("facts about ").length());
                    topic = topic.trim();
                } else if(command.startsWith("facts."))
                {
                    topic = command.substring(command.indexOf("facts.") + ("facts.").length());
                    topic = topic.trim();
                }

                if(topic.isEmpty())
                {
                    local_sendMessage(sender, line_header() + "sorry, but you need to specify a topic for your query!");
                } else
                {
                    Vector<String> tmp = facts.get(topic);
                    if((tmp == null) || (tmp.size() == 0))
                    {
                        local_sendMessage(channel, line_header() + "Sorry, but unfortunately I don't know anything about " + topic + ". :(");
        
                    } else
                    {
                        String factString = "Wait, you want to know everything about " + topic + "? Well, I know " + tmp.size() + " things. Here goes. ";
                        for(int i = 0; i <tmp.size(); i++)
                            factString += (i+1) + ") " + tmp.elementAt(i) + (((i+1) < tmp.size()) ? "; " : "");
                        local_sendMessage_carefully(channel, sender, line_header() + factString);
                    }
                }

            } else if(command.startsWith("fact"))
            {
                String topic = "", factIndex = "";
                int whichFact = -1;

                // is there a subtopic?
                if(command.startsWith("fact."))
                {
                    String tmp = command.substring(command.indexOf("fact.") + ("fact.").length());
                    topic = tmp.trim();

                } else if(command.startsWith("fact about "))
                {
                    String tmp = command.substring(command.indexOf("fact about ") + ("fact about ").length());
                    topic = tmp.trim();
                }

                // if there's following text, try and parse it to a number
                if(topic.contains(" "))
                {
                    factIndex = topic.substring(topic.indexOf(" ") + 1);
                    topic = topic.substring(0, topic.indexOf(" "));
                    System.out.println("fact index is " + factIndex);
                }

                try
                {
                    whichFact = Integer.parseInt(factIndex) - 1;
                } catch(Exception e) {};

                if(topic.isEmpty())
                {
                    sendRandomFact(channel);
                } else
{
                    Vector<String> tmp = facts.get(topic);
                    if((tmp == null) || (tmp.size() == 0))
                    {
                        local_sendMessage(channel, line_header() + "Sorry, but unfortunately I don't know anything about " + topic + ". :(");
        
                    } else
                    {
                        if(whichFact == -1)
                        {
                            whichFact = (int)(tmp.size()*Math.random());
                            local_sendMessage_carefully(channel, sender, line_header() + "Let me tell you something random about " + topic + "! Fact #" + (whichFact+1) + ": " + tmp.elementAt(whichFact));
                        } else
                        {
                            if(whichFact < 0)
                                whichFact = 0;
                            if(whichFact >= tmp.size())
                                whichFact = tmp.size()-1;
                            local_sendMessage_carefully(channel, sender, line_header() + "Let me tell you fact #" + (whichFact+1) + " about " + topic + ": " + tmp.elementAt(whichFact));
                        }
                    }
                }

            } else if(command.startsWith("addfact"))
            {
                String topic = command.substring(command.indexOf("addfact.") + ("addfact.").length());
                topic = topic.substring(0, topic.indexOf(" "));

                if(topic.length() == 0)
                {
                    local_sendMessage(sender, line_header() + "sorry, but you need to specify a topic for your fact! Something like:");
                    local_sendMessage(sender, getNick() + ": addfact.cats Cats have nine lives.");
                } else
                {
                    String fact = commandCase.substring(command.indexOf("addfact"));
                    fact = fact.substring(fact.indexOf(" ")).trim();

                    if(facts.get(topic) == null)
                    {
                        Vector<String> tmp = new Vector<String>();
                        tmp.add(fact);
                        facts.put(topic, tmp);
                    } else
                    {
                        Vector<String> tmp = facts.get(topic);

                        tmp.add(fact);
                        facts.put(topic, tmp);
                    }

                    local_sendMessage(sender, line_header() + "Thanks! I now know " + facts.get(topic).size() + " thing[s] about " + topic + "!");
                }
            } else if(command.startsWith("deletefact"))
            {
                String topic = command.substring(command.indexOf("deletefact.") + ("deletefact.").length());
                topic = topic.substring(0, topic.indexOf(" "));

                if(topic.length() == 0)
                {
                    local_sendMessage(sender, line_header() + "sorry, but you need to specify a topic for your fact! Something like:");
                    local_sendMessage(sender, getNick() + ": deletefact.cats 3");
                } else
                {
                    String whichFact = commandCase.substring(command.indexOf("deletefact"));
                    whichFact = whichFact.substring(whichFact.indexOf(" ")).trim();
                    
                    int x = 0;
                    try
                    {
                        x = Integer.parseInt(whichFact) - 1;

                        if(facts.get(topic) == null)
                        {
                            local_sendMessage(sender, line_header() + "sorry, but I don't know any facts about that topic!");
                        } else
                        {
                            Vector<String> tmp = facts.get(topic);

                            if((x < 0) || (x > tmp.size()))
                            {
                                local_sendMessage(sender, line_header() + "sorry, but the fact you want me to delete doesn't exist. I only know " + tmp.size() + " things about " + topic);
                            } else
                            {
                                local_sendMessage(sender, line_header() + "I have removed the fact \"" + tmp.elementAt(x) + "\" from topic " + topic + ". Hope you're not changing history for the worse. I now know " + (tmp.size() - 1) + " thing[s] about " + topic + ".");
                                tmp.removeElementAt(x);
                                facts.put(topic, tmp);
                            }
                        }

                    } catch(Exception e)
                    {
                        local_sendMessage(sender, line_header() + "sorry, but you need to specify a fact number to remove! Something like:");
                        local_sendMessage(sender, getNick() + ": deletefact.cats 3");
                    }
                }
            } else if(command.equals("stats") || command.equals("statistics"))
            {
                String tmp = "Let me tell you what I know. I am keeping track of " + values.size() + " individual scores. ";
                
                {
                    int nlinks = 0;
                    Enumeration<String> key = links.keys();
                    while(key.hasMoreElements())
                    {
                        String k = key.nextElement();
                        nlinks += links.get(k).size();
                    }
                    tmp += "I am also keeping track of " + nlinks + " dependencies between scores. ";
                }

                {
                    int nfacts = 0;
                    Enumeration<String> key = facts.keys();
                    while(key.hasMoreElements())
                    {
                        String k = key.nextElement();
                        nfacts += facts.get(k).size();
                    }
                    tmp += "Finally, I have been trained to recite " + nfacts + " facts about " + facts.size() + " topics! Isn't THAT impressive?";
                    local_sendMessage(channel, line_header() + tmp);
                }

            } else if(command.endsWith("?"))
            {
                // magic 8-ball response
                Random gen = new Random();
                Vector<String> responses = facts.get(MAGIC_RESPONSE_CATEGORY);
                if(responses.size() == 0)
                {
                    local_sendMessage(channel, line_header() + sender + ": I'm out of responses. :( try adding some to fact category " + MAGIC_RESPONSE_CATEGORY);
                } else
                {
                    String response = responses.elementAt(gen.nextInt(responses.size()));
                    local_sendMessage(channel, line_header() + sender + ": " + response);
                }
            } else
            {
                local_sendMessage(sender, line_header() + "sorry, but I didn't understand your command!");
            }
        }
    }
    
    public void onPrivateMessage(String sender, String login, String hostname, String message)
    {
        onMessage(sender, sender, login, hostname, message);
    }

    public void onAction(String sender, String login, String hostname, String target, String action)
    {
        onMessage(target, sender, login, hostname, action);
    }

    public void onMessage(String channel, String sender, String login, String hostname, String message)
    {
        if(sender.equals(getNick()))
            return;

        // process sunglasses
        if(sunglassesWaitlist.contains(sender.toLowerCase()))
        {
            sunglassesWaitlist.removeElement(sender.toLowerCase());
            local_sendMessage("#" + this.channel, line_header() + sender + ": YEAAAAAAAAAAAAHHHHH");
        }

        checkReminders(sender);

        for(int i = 0; i < blacklistUsers.length; i++)
        {
            if(sender.equalsIgnoreCase(blacklistUsers[i]))
                return;
        }

        System.out.println("message on channel " + channel);

        Vector<String> matchplus    = getMatches("(" + KEY_REGEX + ")\\+\\+", message);
        Vector<String> matchminus   = getMatches("(" + KEY_REGEX + ")--", message);
        Vector<String> matchneutral = getMatches("(" + KEY_REGEX + ")~~", message);

        if(matchplus.contains(sender.toLowerCase()))
        {
            local_sendMessage(sender, line_header() + "hey, " + sender + "--, stop jerking off in public");
            applyMatch(getNick(), sender, sender.toLowerCase(), -1, false);
            matchplus.remove(sender.toLowerCase());
        }

        if(matchplus.contains(getNick().toLowerCase()))
        {
            local_sendMessage(channel, line_header() + "hey, " + sender + ", what are you doing later? *bite*");
        }

        if(matchminus.contains(getNick().toLowerCase()))
        {
            local_sendMessage(channel, line_header() + "hey, " + sender + "--, eat a bag of dicks");
            applyMatch(getNick(), channel, sender, -1, false);
            matchminus.remove(getNick());
        }

        boolean lame = false;
        lame |= applyMatches(sender, channel, matchplus, 1, true);
        lame |= applyMatches(sender, channel, matchminus, -1, true);
        lame |= applyMatches(sender, channel, matchneutral, 0, false);
        if(lame)
        {
            local_sendMessage(sender, line_header() + "hey, " + sender + "--, stop being a dickbag by trying to multi-parse");
            applyMatch(getNick(), sender, sender, -1, false);
        }

        processCommands(channel, sender, message);

        // process triggers
        for(int i = 0; i < triggers.length; i++)
        {
            String patternSender = triggers[i][T_NICK];
            if(patternSender.length() != 0)
            {
                if(Boolean.parseBoolean(triggers[i][T_EXACT]))
                {
                    if(!sender.equals(patternSender))
                        continue;
                }
                else
                {
                    if(!sender.toLowerCase().contains(patternSender.toLowerCase()))
                        continue;
                }
            }

            String patternString = "\\b" + triggers[i][T_KEYWORD].toLowerCase() + "\\b";
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(message.toLowerCase());
            if(matcher.find())
            {
                applyMatch(getNick(), channel, triggers[i][T_VARIABLE], Integer.parseInt(triggers[i][T_DELTA]), false);

                synchronized(pendingParseResults)
                {
                    Parse p = new Parse();
                    p.sender = sender;
                    p.channel = channel;
                    p.key = triggers[i][T_VARIABLE];
                    pendingParseResults.addElement(p);
                    pendingResultsTimer.cancel();
                    pendingResultsTimer = new Timer();
                    pendingResultsTimer.schedule(new PendingResultsTask(), PENDING_RESULTS_TIMER_MILLIS);
                }
            }
        }

        saveData();
    }


    private void saveData()
    {
        try
        {
            {
                File f = new File(data_file);
                if(f.exists())
                    f.renameTo(new File(data_file_backup));
                else
                {
                    System.out.println("ERROR: data file didn't exist (" + data_file + ")");
                    System.exit(-1);
                }
        
                // write new data file
                FileWriter outf = new FileWriter(data_file);
                PrintWriter out = new PrintWriter(outf);
                for(String key : values.keySet())
                {
                    out.println(key);
                    out.println(values.get(key).intValue());
                }
                out.close();
            }

            {
                File f = new File(link_file);
                if(f.exists())
                    f.renameTo(new File(link_file_backup));
                else
                {
                    System.out.println("ERROR: link file didn't exist (" + link_file + ")");
                    System.exit(-1);
                }

                // write new link file
                FileWriter outf = new FileWriter(link_file);
                PrintWriter out = new PrintWriter(outf);
                for(Enumeration<String> keys = links.keys(); keys.hasMoreElements(); )
                {
                    String key = keys.nextElement();
                    Vector<String> sources = links.get(key);
                    for(String value : sources)
                    {
                        out.println(key);
                        out.println(value);
                    }
                }
                out.close();
            }

            {
                File f = new File(fact_file);
                if(f.exists())
                    f.renameTo(new File(fact_file_backup));
                else
                {
                    System.out.println("ERROR: fact file didn't exist (" + fact_file + ")");
                    System.exit(-1);
                }

                // write new fact file
                FileWriter outf = new FileWriter(fact_file);
                PrintWriter out = new PrintWriter(outf);
                for(Enumeration<String> keys = facts.keys(); keys.hasMoreElements(); )
                {
                    String key = keys.nextElement();
                    Vector<String> sources = facts.get(key);
                    for(String value : sources)
                    {
                        out.println(key);
                        out.println(value);
                    }
                }
                out.close();
            }

        } catch(IOException ioe)
        {
            System.err.println("ioexception writing data: " + ioe);
            ioe.printStackTrace();
        }
    }

    private void restoreData()
    {
        try
        {
            {
                File f = new File(data_file);
                if(!f.exists())
                {
                    System.out.println("ERROR: data file didn't exist (" + data_file + ")");
                    System.exit(-1);
                }

                // read new data file
                FileReader inf = new FileReader(f);
                BufferedReader in = new BufferedReader(inf);
            
                String line;
                while((line = in.readLine()) != null)
                {
                    String key = line;
                    line = in.readLine();
                    if(line == null)
                    {
                        System.err.println("data file error: no value for key " + key);
                        System.exit(-1);
                    }
        
                    values.put(key, new Integer(Integer.parseInt(line)));
                }
        
                System.out.println("restored " + values.size() + " pairs on launch");
            }
            {
                int lcount = 0;
                File f = new File(link_file);
                if(!f.exists())
                {
                    System.out.println("ERROR: link file didn't exist (" + link_file + ")");
                    System.exit(-1);
                }

                // read links
                FileReader inf = new FileReader(f);
                BufferedReader in = new BufferedReader(inf);
            
                String line;
                while((line = in.readLine()) != null)
                {
                    String key = line;
                    line = in.readLine();
                    if(line == null)
                    {
                        System.err.println("data file error: no target for key " + key);
                        System.exit(-1);
                    }

                    if(links.get(key) == null)
                    {
                        Vector<String> targets = new Vector<String>();
                        targets.add(line);
                        links.put(key, targets);
                    } else
                    {
                        Vector<String> targets = links.get(key);
                        targets.add(line);
                        links.put(key, targets);
                    }

                    lcount++;
                }
        
                System.out.println("restored " + lcount + " links on launch");
            }
            {
                int lcount = 0;
                File f = new File(fact_file);
                if(!f.exists())
                {
                    System.out.println("ERROR: fact file didn't exist (" + fact_file + ")");
                    System.exit(-1);
                }

                // read facts
                FileReader inf = new FileReader(f);
                BufferedReader in = new BufferedReader(inf);
            
                String line;
                while((line = in.readLine()) != null)
                {
                    String key = line;
                    line = in.readLine();
                    if(line == null)
                    {
                        System.err.println("data file error: no target for key " + key);
                        System.exit(-1);
                    }

                    if(facts.get(key) == null)
                    {
                        Vector<String> targets = new Vector<String>();
                        targets.add(line);
                        facts.put(key, targets);
                    } else
                    {
                        Vector<String> targets = facts.get(key);
                        targets.add(line);
                        facts.put(key, targets);
                    }

                    lcount++;
                }
        
                System.out.println("restored " + lcount + " facts on launch");
            }
        } catch(IOException ioe)
        {
            System.err.println("ioexception restoring data: " + ioe);
            ioe.printStackTrace();
            System.exit(-1);
        }
    }

    private String line_header()
    {
        //return getNick() + "]] ";
        return "]] ";
    }

    public void sendStatistics(String channel, int n)
    {

        ArrayList<Map.Entry<String, Integer> > entries = new ArrayList<Map.Entry<String, Integer>>(values.entrySet());
        Collections.sort(entries, new EntryComparator());

        if(n > entries.size())
            n = entries.size();

        String message = line_header() + "top " + n + " entries: ";

        for(int i = 0; i < n; i++)
            message += entries.get(i).getKey() + " = " + entries.get(i).getValue().intValue() + " || ";

        local_sendMessage(channel, message);
        message = line_header() + "bottom " + n + " entries: ";

        for(int i = 0; i < n; i++)
            message += entries.get(entries.size() - i - 1).getKey() + " = " + entries.get(entries.size() - i - 1).getValue().intValue() + " || ";
    
        local_sendMessage(channel, message);
    }

    public void sendKeyedStatistics(String channel, String sender, String match)
    {
        String message = line_header() + "entries containing \"" + match + "\": ";
        int count = 0;

        String key;
        Enumeration<String> keys = values.keys();
        while(keys.hasMoreElements())
        {
            key = keys.nextElement();
            if(key.toLowerCase().contains(match.toLowerCase()))
            {
                count++;
                message += valueString(key, false) + " || ";
            }
        }
        message += "(" + count + " matches found)";
        local_sendMessage_carefully(channel, sender, message);
    }

    public void sendKeyedLinkStatistics(String channel, String sender, String match)
    {
        String message = line_header() + "things that are dependent on \"" + match + "\": ";
        int count = 0;

        String key;
        Enumeration<String> keys = values.keys();
        while(keys.hasMoreElements())
        {
            key = keys.nextElement();
            Vector<String> targets = links.get(key);
            if((targets != null) && targets.contains(match.toLowerCase()))
            {
                count++;

                if(count < 50)
                    message += key + ": " + values.get(key).intValue() + " || ";
            }
        }

        if(count >= 50)
            message += "[truncated] ";
        message += "(" + count + " matches found)";
        local_sendMessage_carefully(channel, sender, message);
    }

    protected void onDisconnect()
    {
        while(!isConnected())
        {
            try 
            {
                System.out.println("disconnected!");
                connect("irc.freenode.net");
                joinChannel("#" + channel);
            } catch(Exception e)
            {
                System.out.println("failed to reconnect");
                e.printStackTrace();
                try
                {
                    java.lang.Thread.sleep(1000);
                } catch(Exception e2) {}
            }
        } 

/*
        GregorianCalendar lolcal = new GregorianCalendar();
        if(lolcal.get(Calendar.DAY_OF_WEEK) == lolcal.FRIDAY)
            changeNick(getNick().toUpperCase());
        else
            changeNick(getNick().toLowerCase());
            */
    }

    public String valueString(String key, boolean trim)
    {
        // find related values
        String suffix = "";
        key = key.toLowerCase();
        int trimMaxLen = MAX_MESSAGE_LEN - key.length() - 3 - 5 - getNick().length();

        int sum;
        try
        {
            sum = values.get(key).intValue();
        } catch(Exception e)
        {
            sum = 0;
        }

        if(trim)
        {
            /* assemble all potential suffixes into a new vector
             * and then select some randomly to be below the max
             * message length
             */
            Vector<String> targets = links.get(key);
            if(targets != null)
            {
                Vector<String> suffixes = new Vector<String>();

                suffix += " (" + values.get(key) + ") linked to: ";

                for(int i = 0; i < targets.size(); i++)
                {
                    String otherKey = targets.elementAt(i);
                    Integer tmp = values.get(otherKey);

                    suffixes.add(userNameFilter(otherKey) + ": " + tmp.intValue());
                    sum += tmp.intValue();
                }

                /* randomly select */
                Random gen = new Random();
                while(suffixes.size() > 0)
                {
                    String old = suffix;
                    // select one randomly
                    int which = gen.nextInt(suffixes.size());
                    suffix += suffixes.elementAt(which) + ", ";
                    suffixes.remove(which);

                    if(suffix.length() > trimMaxLen)
                    {
                        suffix = old;
                        break;
                    }
                }
            }
        } else
        {
            /* no trim; just add normally */
            Vector<String> targets = links.get(key);
            if(targets != null)
            {
                suffix += " (" + values.get(key) + ") linked to: ";

                for(int i = 0; i < targets.size(); i++)
                {
                    String otherKey = targets.elementAt(i);
                    Integer tmp = values.get(otherKey);

                    suffix += userNameFilter(otherKey) + ": " + tmp.intValue() + ((i != (targets.size() - 1)) ? ", " : "");

                    sum += tmp.intValue();
                }
            }
        }

        return userNameFilter(key) + " = " + sum + suffix;
    }

    public void displayValue(String channel, String sender, String key)
    {
        local_sendMessage_carefully(channel, sender, line_header() + valueString(key, true) + "\n");
    }

    /* filter out any matching usernames so they don't get pinged */
    public String userNameFilter(String what)
    {
        String lower = what.toLowerCase();

        String remove = null;

        /* find any matches */
        User[] users = getUsers("#" + channel);
        for(int i = 0; i < users.length; i++)
        {
            StringTokenizer st = new StringTokenizer(users[i].getNick(), "|-_|`");
            String user = st.nextToken();

            if((user.length() > 2) && lower.contains(user.toLowerCase()))
            {
                remove = user;
                break;
            }
        }

        for(int i = 0; i < FILTERED_STRINGS.length; i++)
        {
            String str = FILTERED_STRINGS[i];

            if(lower.contains(str.toLowerCase()))
            {
                remove = str;
                break;
            }
        }

        if(remove == null)
            return what;

        /* figure out where to insert the _ */
        int subIndex = lower.indexOf(remove.toLowerCase());
        Random r = new Random();
    
        subIndex += r.nextInt(remove.length());

        System.out.println("filtering " + remove + " from position " + subIndex);

        /* build response */
        String ret = "";

        if(subIndex > 0)
            ret += what.substring(0, subIndex);

        ret += "#";
            
        if((subIndex+1) < what.length())
            ret += what.substring(subIndex+1);

        return ret;
    }

    public void sendRandomFact(String channel)
    {
        // make list of all facts
        Vector<String> allfacts = new Vector<String>();

        Enumeration<String> key = facts.keys();
        while(key.hasMoreElements())
        {
            String topic = key.nextElement();

            Vector<String> tmp = facts.get(topic);
            for(int i = 0; i < tmp.size(); i++)
                allfacts.add(topic + "! Fact #" + (i+1) + ": " + tmp.elementAt(i));
        }

        int whichFact = (int)(allfacts.size()*Math.random());
        local_sendMessage(channel, line_header() + "Let me tell you something random about " + allfacts.elementAt(whichFact));
    }

    public void local_sendMessage(String channel, String message)
    {
        /*
        GregorianCalendar lolcal = new GregorianCalendar();
        if(lolcal.get(Calendar.DAY_OF_WEEK) == lolcal.FRIDAY)
        {
            message = message.toUpperCase();
            changeNick(getNick().toUpperCase());
        } else
        {
            changeNick(getNick().toLowerCase());
        }
        */

        boolean needsSplit = message.length() > MAX_MESSAGE_LEN;

        System.out.println("maxline = " + MAX_MESSAGE_LEN);

        if(!needsSplit)
        {
            sendMessage(channel, message);
            return;
        }

        while(message.length() > MAX_MESSAGE_LEN)
        {
            sendMessage(channel, message.substring(0, MAX_MESSAGE_LEN));
            message = message.substring(MAX_MESSAGE_LEN, message.length());
        }
        sendMessage(channel, message);
    }

    public void local_sendMessage_carefully(String channel, String sender, String message)
    {
        if(!channel.equals(sender) && (message.length() > MAX_MESSAGE_LEN))
        {
            String tooLong = line_header() + "Hey, the response to your query was too fucking long (" + (message.length() / MAX_MESSAGE_LEN) + " pages.) Here's the first page, and your good friend " + sender + " can have the rest via PM. If you want to see the full results, run that shit yourself.";

            local_sendMessage(channel, tooLong);
            local_sendMessage(channel, message.substring(0, MAX_MESSAGE_LEN-3) + "...");
            local_sendMessage(sender, message);
        } else
        {
            local_sendMessage(channel, message);
        }
    }

    protected void onInvite(String target, String source, String sourceLogin, String sourceHost, String inviteChannel)
    {
        // let's be social! and join the channel
        if(channel.contains(inviteChannel) || inviteChannel.contains(channel))
        {
            joinChannel(inviteChannel);
            local_sendMessage(inviteChannel, "Hi guys. In case you were wondering, it's " + source + "'s fault I'm here.");
        }
    }

    private Reminder parseReminder(String[] regexes, String sender, String message, int target_idx, int date_idx, int message_idx) throws ParseException
    {
        boolean found = false;
        Pattern pattern = null;
        Matcher matcher = null;

        for(int i = 0; i < regexes.length; i++)
        {
            pattern = Pattern.compile(regexes[i]);
            matcher = pattern.matcher(message);

            if(matcher.find())
            {
                System.out.println("matched pattern " + regexes[i]);
                for(int j = 0; j < matcher.groupCount()+1; j++)
                {
                    System.out.println("group " + j + " = " + matcher.group(j));
                }
                found = true;
                break;
            }

        }

        if(!found)
            return null;

        Reminder r = new Reminder();
        r.destination = matcher.group(target_idx).trim();
        r.sender = sender.toLowerCase();

        if(r.destination.equalsIgnoreCase("me"))
            r.destination = sender;

        r.when_expired = 0;

        if(date_idx <= 0)
        {
            r.when = 0;
        } else
        {
            String date = matcher.group(date_idx).trim();
            Date realDate = DateUtil.parse(matcher.group(date_idx));

            if(realDate.getYear() == 70)
                realDate.setYear((new Date()).getYear());

            if((realDate.getMonth() == 0) && (realDate.getDate() == 1))
            {
                realDate.setMonth((new Date()).getMonth());
                realDate.setDate((new Date()).getDate());
            }

            r.when = realDate.getTime();
        }

        r.message = matcher.group(message_idx).trim();
        r.created = new Long((new Date()).getTime());

        return r;
    }

    public void checkReminders(String sender)
    {
        synchronized(reminders)
        {
            sender = sender.toLowerCase();

            if(reminders.containsKey(sender))
            {
                Vector<Reminder> forUser = reminders.get(sender);
                Vector<Reminder> timed = new Vector<Reminder>();

                for(Reminder r : forUser)
                {
                    if(r.when == 0)
                        local_sendMessage(r.destination, sender + ": " + r.toString());
                    else
                        timed.addElement(r);
            //        local_sendMessage(r.channel, sender + ": " + r.toString());
                }

                reminders.put(sender, timed);
            }

            // update activity record
            activityTracker.put(sender, new Long((new Date()).getTime()));
        }
    }

    public void checkTimedReminders()
    {
        long now = (new Date()).getTime();

        synchronized(reminders)
        {
            // find any timed reminders that have elapsed now
            for(String dst : reminders.keySet())
            {
                Vector<Reminder> dstReminders = reminders.get(dst);
                Vector<Reminder> leftReminders = new Vector<Reminder>();
                for(Reminder r : dstReminders)
                {
                    if((r.when != 0) && (r.when < now))
                    {
                        local_sendMessage(r.destination, r.destination + ": " + r.toString());
                        //local_sendMessage(r.channel, r.destination + ": " + r.toString());
                        
                        boolean remove = false;

                        // if the user has been active recently, kill the reminder
                        // if not, drop the timed part and remind them once more when they're active
                        if(activityTracker.containsKey(r.destination))
                        {
                            Long timestamp = activityTracker.get(r.destination);
                            if(timestamp.longValue() > (now - RECENT_ACTIVITY_MILLISECONDS))
                                remove = true;
                        }

                        if(remove)
                        {
                            // do nothing
                        } else
                        {
                            r.when_expired = r.when;
                            r.when = 0;
                            leftReminders.addElement(r);
                        }
                    } else
                    {
                        leftReminders.addElement(r);
                    }
                }

                reminders.put(dst, leftReminders);
            }
        }
    }

    public void postPendingResults()
    {
        synchronized(pendingParseResults)
        {
            System.out.println("pending parse task");

            Vector<String> uniqueList = new Vector<String>();

            {
                Iterator<Parse> i = pendingParseResults.iterator();
                while(i.hasNext())
                {
                    Parse current = i.next();
                    if(uniqueList.contains(current.key))
                        continue;
                    else
                        uniqueList.addElement(current.key);
                }
            }

            int len = uniqueList.size();
            String msg = line_header();
            int sublen = ((MAX_MESSAGE_LEN-msg.length()) / len) - 5;

            for(int i = 0; i < uniqueList.size(); i++)
            {
                String tmp = valueString(uniqueList.elementAt(i), true);

                if(tmp.length() > sublen)
                    tmp = tmp.substring(0, sublen) + "...; ";
                else
                    tmp += "; ";

                msg += tmp;
            }
           
            local_sendMessage("#" + channel, msg);
            pendingParseResults.clear();
        }
    }
}
