import org.jibble.pircbot.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.regex.*;
import java.io.*;

public class PPbot extends PircBot
{

	final String[][] triggers = {/*{"", "pot",	"hey, can I get a hit of that?"},
					 {"", "weed",	"hey, can I get a hit of that?"},
					 {"", "dongs",	"SMELLS LIKE MAN MEAT"},
					 {"", "dong",	"SMELLS LIKE MAN MEAT"},*/
					{"jtb", "show", "jonthebastard.mentions.a.show++"},
					{"jonthebastard", "show", "jonthebastard.mentions.a.show++"},
					{"jtb", "shows", "jonthebastard.mentions.a.show++"},
					{"jonthebastard", "shows", "jonthebastard.mentions.a.show++"},
					{"jtb", "concert", "jonthebastard.mentions.a.show++"},
					{"jonthebastard", "concert", "jonthebastard.mentions.a.show++"},
					{"jtb", "concerts", "jonthebastard.mentions.a.show++"},
					{"jonthebastard", "concerts", "jonthebastard.mentions.a.show++"}};

	final String[] blacklistUsers = {"dongbot"};
	final String[] blacklistKeys = {"gogurt"};

	static final String KEY_REGEX = "[\\[\\]\\w\\._-|\\{\\}]{2,}";

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

	public void applyMatch(String sender, String channel, String key, int delta, boolean checkExpiry)
	{
		for(int i = 0; i < blacklistKeys.length; i++)
		{
			if(key.equalsIgnoreCase(blacklistKeys[i]))
			{
				sendMessage(sender, line_header() + "sorry, but " + key +" has been identified as a topic of great contention and been blacklisted");
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
					sendMessage(sender, line_header() + "sorry, " + sender + ", but you can't change \"" + key + "\" for another " + format.format(expiry));
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
					sendMessage(sender, line_header() + "sorry, but I didn't understand the argument to that command!");
					e.printStackTrace();
				}

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
				while(m.find())
				{
					System.out.println("linking " + m.group(1) + " to " + m.group(2));

					String dest = m.group(1).toLowerCase();
					String src = m.group(2).toLowerCase();

					if(dest.equals(src))
					{
							sendMessage(sender, line_header() + "sorry, but " + src + " can't be linked to itself!");
							continue;
					} else if(dest.equalsIgnoreCase(sender))
					{
							sendMessage(sender, line_header() + "sorry, but you can't link things to yourself!");
							continue;
					}

					// make sure source exists
					if(values.get(src) == null)
						values.put(src, new Integer(0));

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
							sendMessage(sender, line_header() + "sorry, but " + src + " is already linked to " + dest);
							continue;
						}

						targets.add(src);
						links.put(dest, targets);
					}

					if(!postUpdates.contains(dest))
						postUpdates.add(dest);

					sendMessage(sender, line_header() + "I have linked the key \"" + dest + "\" so that it is now dependent on \"" + src + "\"!");
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
							sendMessage(sender, line_header() + "sorry, but " + src + " can't be linked to itself!");
							continue;
					} else if(dest.equalsIgnoreCase(sender))
					{
							sendMessage(sender, line_header() + "sorry, but you can't unlink things to yourself!");
							continue;
					}

					if(links.get(dest) == null)
					{
						sendMessage(sender, line_header() + "sorry, but " + src + " isn't linked to " + dest);
					} else
					{
						Vector<String> targets = links.get(dest);

						if(targets.contains(src))
						{
							targets.remove(src);
							links.put(dest, targets);
						} else
						{
							sendMessage(sender, line_header() + "sorry, but " + src + " isn't linked to " + dest);
							continue;
						}
					}

					if(!postUpdates.contains(dest))
						postUpdates.add(dest);

					sendMessage(sender, line_header() + "I have unlinked the key \"" + dest + "\" so that it is no longer dependent on \"" + src + "\"!");
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

			} else if(command.equalsIgnoreCase("rimshot"))
			{
				sendMessage(channel, line_header() + "ba-dum-tish!");
			} else if(command.equalsIgnoreCase("rimjob"))
			{
				sendMessage(channel, line_header() + "ba-dum-tush!");
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
					sendMessage(sender, line_header() + "sorry, but you need to specify a topic for your query!");
				} else
				{
					Vector<String> tmp = facts.get(topic);
					if((tmp == null) || (tmp.size() == 0))
					{
						sendMessage(channel, line_header() + "Sorry, but unfortunately I don't know anything about " + topic + ". :(");
		
					} else
					{
						String factString = "Wait, you want to know everything about " + topic + "? Well, I know " + tmp.size() + " things. Here goes. ";
						for(int i = 0; i <tmp.size(); i++)
							factString += (i+1) + ") " + tmp.elementAt(i) + (((i+1) < tmp.size()) ? "; " : "");
						sendMessage(channel, line_header() + factString);
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
						sendMessage(channel, line_header() + "Sorry, but unfortunately I don't know anything about " + topic + ". :(");
		
					} else
					{
						if(whichFact == -1)
						{
							whichFact = (int)(tmp.size()*Math.random());
							sendMessage(channel, line_header() + "Let me tell you something random about " + topic + "! Fact #" + (whichFact+1) + ": " + tmp.elementAt(whichFact));
						} else
						{
							if(whichFact < 0)
								whichFact = 0;
							if(whichFact >= tmp.size())
								whichFact = tmp.size()-1;
							sendMessage(channel, line_header() + "Let me tell you fact #" + (whichFact+1) + " about " + topic + ": " + tmp.elementAt(whichFact));
						}
					}
				}

			} else if(command.startsWith("addfact"))
			{
				String topic = command.substring(command.indexOf("addfact.") + ("addfact.").length());
				topic = topic.substring(0, topic.indexOf(" "));

				if(topic.length() == 0)
				{
					sendMessage(sender, line_header() + "sorry, but you need to specify a topic for your fact! Something like:");
					sendMessage(sender, getNick() + ": addfact.cats Cats have nine lives.");
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

					sendMessage(sender, line_header() + "Thanks! I now know " + facts.get(topic).size() + " thing[s] about " + topic + "!");
				}
			} else if(command.startsWith("deletefact"))
			{
				String topic = command.substring(command.indexOf("deletefact.") + ("deletefact.").length());
				topic = topic.substring(0, topic.indexOf(" "));

				if(topic.length() == 0)
				{
					sendMessage(sender, line_header() + "sorry, but you need to specify a topic for your fact! Something like:");
					sendMessage(sender, getNick() + ": deletefact.cats 3");
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
							sendMessage(sender, line_header() + "sorry, but I don't know any facts about that topic!");
						} else
						{
							Vector<String> tmp = facts.get(topic);

							if((x < 0) || (x > tmp.size()))
							{
								sendMessage(sender, line_header() + "sorry, but the fact you want me to delete doesn't exist. I only know " + tmp.size() + " things about " + topic);
							} else
							{
								sendMessage(sender, line_header() + "I have removed the fact \"" + tmp.elementAt(x) + "\" from topic " + topic + ". Hope you're not changing history for the worse. I now know " + (tmp.size() - 1) + " thing[s] about " + topic + ".");
								tmp.removeElementAt(x);
								facts.put(topic, tmp);
							}
						}

					} catch(Exception e)
					{
						sendMessage(sender, line_header() + "sorry, but you need to specify a fact number to remove! Something like:");
						sendMessage(sender, getNick() + ": deletefact.cats 3");
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
					sendMessage(channel, line_header() + tmp);
				}
					
			} else
			{
				sendMessage(sender, line_header() + "sorry, but I didn't understand your command!");
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
			sendMessage(sender, line_header() + "hey, " + sender + "--, stop jerking off in public");
			applyMatch(getNick(), sender, sender.toLowerCase(), -1, false);
			matchplus.remove(sender.toLowerCase());
		}

		if(matchplus.contains(getNick().toLowerCase()))
		{
			sendMessage(channel, line_header() + "hey, " + sender + ", what are you doing later? *bite*");
		}

		if(matchminus.contains(getNick().toLowerCase()))
		{
			sendMessage(channel, line_header() + "hey, " + sender + "--, eat a bag of dicks");
			applyMatch(getNick(), channel, sender, -1, false);
			matchminus.remove(getNick());
		}

		boolean lame = false;
		lame |= applyMatches(sender, channel, matchplus, 1, true);
		lame |= applyMatches(sender, channel, matchminus, -1, true);
		lame |= applyMatches(sender, channel, matchneutral, 0, false);
		if(lame)
		{
			sendMessage(sender, line_header() + "hey, " + sender + "--, stop being a dickbag by trying to multi-parse");
			applyMatch(getNick(), sender, sender, -1, false);
		}

		processCommands(channel, sender, message);

		// process triggers
		for(int i = 0; i < triggers.length; i++)
		{
			String patternSender = triggers[i][0];
			if(patternSender.length() != 0)
			{
				if(!sender.contains(patternSender))
					continue;
			}

			String patternString = "\\b" + triggers[i][1].toLowerCase() + "\\b";
			Pattern pattern = Pattern.compile(patternString);
			Matcher matcher = pattern.matcher(message.toLowerCase());
			if(matcher.find())
			{
				sendMessage(channel, sender + ": " + triggers[i][2]);
				onMessage(channel, getNick() + "_auto", getNick(), "", triggers[i][2]);
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
					System.out.println("warning: data file didn't exist");
		
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
					System.out.println("warning: link file didn't exist");

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
					System.out.println("warning: fact file didn't exist");

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
					System.out.println("warning: data file didn't exist, ignoring");
					return;
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
					System.out.println("warning: link file didn't exist, ignoring");
					return;
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
					System.out.println("warning: fact file didn't exist, ignoring");
					return;
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

		sendMessage(channel, message);
		message = line_header() + "bottom " + n + " entries: ";

		for(int i = 0; i < n; i++)
			message += entries.get(entries.size() - i - 1).getKey() + " = " + entries.get(entries.size() - i - 1).getValue().intValue() + " || ";
	
		sendMessage(channel, message);
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
				message += valueString(key) + " || ";
			}
		}
		message += "(" + count + " matches found)";
		sendMessage(channel, message);
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

	public String valueString(String key)
	{
		// find related values
		String suffix = "";
		key = key.toLowerCase();

		int sum = values.get(key).intValue();

		Vector<String> targets = links.get(key);
		if(targets != null)
		{
			suffix += " (itself, " + values.get(key) + ") is dependent on values: ";

			for(int i = 0; i < targets.size(); i++)
			{
				String otherKey = targets.elementAt(i);
				Integer tmp = values.get(otherKey);
				suffix += otherKey + ": " + tmp.intValue() + ((i != (targets.size() - 1)) ? ", " : "");
				sum += tmp.intValue();
			}
		}

		return key + " = " + sum + suffix;
	}

	public void displayValue(String channel, String key)
	{
		sendMessage(channel, line_header() + valueString(key) + "\n");
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
		sendMessage(channel, line_header() + "Let me tell you something random about " + allfacts.elementAt(whichFact));
	}
}
