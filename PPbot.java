import org.jibble.pircbot.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.regex.*;
import java.io.*;

public class PPbot extends PircBot
{

	final String[][] triggers = {/*{"pot",	"hey, can I get a hit of that?"},
					 {"weed",	"hey, can I get a hit of that?"},
					 {"dongs",	"SMELLS LIKE MAN MEAT"},
					 {"dong",	"SMELLS LIKE MAN MEAT"}*/};


	static final String KEY_REGEX = "[\\[\\]\\w\\._|]{2,}";

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

	Hashtable<String, Integer> values = new Hashtable<String, Integer>();
	Vector<Parse> recentParses = new Vector<Parse>();
	Hashtable<String, Vector<String> > links = new Hashtable<String, Vector<String> >();

	String channel;
	String data_file;
	String data_file_backup;
	String link_file;
	String link_file_backup;

    public PPbot(String channel, String name) {
		this.channel = channel;
		data_file = channel + ".dat";
		data_file_backup = channel + ".dat.bak";
		link_file = channel + ".link";
		link_file_backup = channel + ".link.bak";
	
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

		// find any dependent keys, and display them too
		for(Enumeration<String> linkkeys = links.keys(); linkkeys.hasMoreElements();)
		{
			String dest = linkkeys.nextElement();
			if(links.get(dest).contains(key))
			{
				displayValue(channel, dest);
			}
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
		if(message.startsWith(commandHeader))
		{
			String command = message.substring(commandHeader.length()+1).trim().toLowerCase();
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

					sendMessage(channel, line_header() + "I have linked the key \"" + dest + "\" so that it is now dependent on \"" + src + "\"!");
				}

				for(int i = 0; i < postUpdates.size(); i++)
				{
					displayValue(channel, postUpdates.elementAt(i));
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
			} else
			{
				sendMessage(sender, line_header() + "sorry, but I didn't understand your command!");
			}
		}
	}
    
    public void onMessage(String channel, String sender, String login, String hostname, String message)
	{
		if(sender.equals(getNick()))
			return;

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
			String patternString = "\\b" + triggers[i][0].toLowerCase() + "\\b";
			Pattern pattern = Pattern.compile(patternString);
			Matcher matcher = pattern.matcher(message.toLowerCase());
			if(matcher.find())
			{
				sendMessage(channel, sender + ": " + triggers[i][1]);
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
}
