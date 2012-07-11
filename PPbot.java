import org.jibble.pircbot.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.regex.*;
import java.io.*;

public class PPbot extends PircBot
{

	final String[][] triggers = {
		                    //  {string nick, bool exact, string match, string variable, int delta}
					{"danyell", "false", "hah", "danyell.says.hah", 1},
					{"BungoDanderfluff", "true", "meow", "meow", 1},
					{"xx3nvyxx", "true", "meow", "meow", 1},
					{"jtb", "false", "show", "jonthebastard.mentions.a.show", 1},
					{"jonthebastard", "false", "show", "jonthebastard.mentions.a.show", 1},
					{"jtb", "false", "shows", "jonthebastard.mentions.a.show", 1},
					{"jonthebastard", "false", "shows", "jonthebastard.mentions.a.show", 1},
					{"jtb", "false", "concert", "jonthebastard.mentions.a.show", 1},
					{"jonthebastard", "false", "concert", "jonthebastard.mentions.a.show", 1},
					{"jtb", "false", "concerts", "jonthebastard.mentions.a.show", 1},
					{"jonthebastard", "false", "concerts", "jonthebastard.mentions.a.show", 1},
					{"jtb", "false", "gig", "jonthebastard.mentions.a.show", 1},
					{"jonthebastard", "false", "gig", "jonthebastard.mentions.a.show", 1},
					{"jtb", "false", "gigs", "jonthebastard.mentions.a.show", 1},
					{"jonthebastard", "false", "gigs", "jonthebastard.mentions.a.show", 1}};

	final String MAGIC_RESPONSE_CATEGORY = "magic8ball";
	final String[] blacklistUsers = {"dongbot"};
	final String[] blacklistKeys = {"gogurt"};

	static final int MAX_MESSAGE_LEN = 400;

	static final String KEY_REGEX = "[\\[\\]\\w\\._\\-|\\{\\}]{2,}";

	class Parse
	{
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
	static final long RANDOM_FACT_TIMER_MILLISECONDS = 60 * 60 * 1000; // 15 minutes

	Hashtable<String, Integer> values = new Hashtable<String, Integer>();
	Vector<Parse> recentParses = new Vector<Parse>();
	Hashtable<String, Vector<String> > links = new Hashtable<String, Vector<String> >();

	Hashtable<String, Vector<String> > facts = new Hashtable<String, Vector<String> >();

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

		displayValue(channel, key);
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
		if(message.startsWith(commandHeader))
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
				sendKeyedLinkStatistics(channel, arg);

			} else if(command.startsWith("?"))
			{
				String arg = command.substring(1).trim().toLowerCase();
				sendKeyedStatistics(channel, arg);

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
					displayValue(sender, postUpdates.elementAt(i));
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
					displayValue(sender, postUpdates.elementAt(i));
				}

			} else if(command.startsWith("what the fuck is the score of") || command.startsWith("what the fuck is the value of"))
			{
				String arg = command.substring((new String("what the fuck is the score of")).length()).trim().toLowerCase();
				int delim = arg.indexOf("?");
				if(delim != -1)
				{
					arg = arg.substring(0, delim);
				}
				sendKeyedStatistics(channel, arg);

			} else if(command.startsWith("who the fuck cares about"))
			{
				String arg = command.substring((new String("who the fuck cares about")).length()).trim().toLowerCase();
				int delim = arg.indexOf("?");
				if(delim != -1)
				{
					arg = arg.substring(0, delim);
				}
				sendKeyedLinkStatistics(channel, arg);

			} else if(command.equalsIgnoreCase("rimshot"))
			{
				local_sendMessage(channel, line_header() + "ba-dum-tish!");
			} else if(command.equalsIgnoreCase("rimjob"))
			{
				local_sendMessage(channel, line_header() + "ba-dum-tush!");
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
						local_sendMessage(channel, line_header() + factString);
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
							local_sendMessage(channel, line_header() + "Let me tell you something random about " + topic + "! Fact #" + (whichFact+1) + ": " + tmp.elementAt(whichFact));
						} else
						{
							if(whichFact < 0)
								whichFact = 0;
							if(whichFact >= tmp.size())
								whichFact = tmp.size()-1;
							local_sendMessage(channel, line_header() + "Let me tell you fact #" + (whichFact+1) + " about " + topic + ": " + tmp.elementAt(whichFact));
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

    public void onMessage(String channel, String sender, String login, String hostname, String message)
	{
		if(sender.equals(getNick()))
			return;

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
			String patternSender = triggers[i][0];
			if(patternSender.length() != 0)
			{
				if(Boolean.parseBoolean(triggers[i][1]))
				{
					if(!sender.equals(patternSender)
						continue;
				}
				else
				{
					if(!sender.toLowerCase().contains(patternSender.toLowerCase()))
						continue;
				}
			}

			String patternString = "\\b" + triggers[i][2].toLowerCase() + "\\b";
			Pattern pattern = Pattern.compile(patternString);
			Matcher matcher = pattern.matcher(message.toLowerCase());
			if(matcher.find())
			{
				local_sendMessage(channel, sender + ": " + triggers[i][3] + "++");
				applyMatch(getNick(), "#"+channel, triggers[i][3], Integer.parseInt(triggers[i][4]), false);
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
					System.out.println("ERROR: data file didn't exist");
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
					System.out.println("ERROR: link file didn't exist");
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
					System.out.println("ERROR: fact file didn't exist");
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
					System.out.println("ERROR: data file didn't exist, ignoring");
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
					System.out.println("ERROR: link file didn't exist, ignoring");
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
					System.out.println("ERROR: fact file didn't exist, ignoring");
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

	public void sendKeyedStatistics(String channel, String match)
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
		local_sendMessage(channel, message);
	}

	public void sendKeyedLinkStatistics(String channel, String match)
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
		local_sendMessage(channel, message);
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

					suffixes.add(otherKey + ": " + tmp.intValue());
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

					suffix += otherKey + ": " + tmp.intValue() + ((i != (targets.size() - 1)) ? ", " : "");

					sum += tmp.intValue();
				}
			}
		}

		return key + " = " + sum + suffix;
	}

	public void displayValue(String channel, String key)
	{
		local_sendMessage(channel, line_header() + valueString(key, true) + "\n");
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
		GregorianCalendar lolcal = new GregorianCalendar();
		if(lolcal.get(Calendar.DAY_OF_WEEK) == lolcal.FRIDAY)
		{
			message = message.toUpperCase();
			changeNick(getNick().toUpperCase());
		} else
		{
			changeNick(getNick().toLowerCase());
		}

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

	protected void onInvite(String target, String source, String sourceLogin, String sourceHost, String inviteChannel)
	{
		// let's be social! and join the channel
		if(channel.contains(inviteChannel) || inviteChannel.contains(channel))
		{
			joinChannel(inviteChannel);
			local_sendMessage(inviteChannel, "Hi guys. In case you were wondering, it's " + source + "'s fault I'm here.");
		}
	}
}
