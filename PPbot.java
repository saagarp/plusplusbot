import org.jibble.pircbot.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.regex.*;
import java.io.*;

public class PPbot extends PircBot
{

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

	String data_file;
	String data_file_backup;

    public PPbot(String channel, String name) {
		data_file = channel + ".dat";
		data_file_backup = channel + ".bak";
	
	this.setAutoNickChange(true);
        this.setName(name);
		this.identify("5tr1p4m3");

		restoreData();
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

		sendMessage(channel, line_header() + key + " = " + values.get(key).intValue() + "\n");
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
    
    public void onMessage(String channel, String sender, String login, String hostname, String message)
	{
		if(sender.equals(getNick()))
			return;

		String chars = "[\\[\\]\\w\\._|]";

		Vector<String> matchplus    = getMatches("(" + chars + "{2,})\\+\\+", message);
		Vector<String> matchminus   = getMatches("(" + chars + "{2,})--", message);
		Vector<String> matchneutral = getMatches("(" + chars + "{2,})~~", message);

		if(matchplus.contains(sender.toLowerCase()))
		{
			sendMessage(sender, line_header() + "hey, " + sender + "--, stop jerking off in public");
			applyMatch(getNick(), sender, sender, -1, false);
			matchplus.remove(sender);
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


		// process commands only if we're specifically targetted
		{
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

				} else if(command.startsWith("what the fuck is the score of") || command.startsWith("what the fuck is the value of"))
				{
					String arg = command.substring((new String("what the fuck is the score of")).length()).trim().toLowerCase();
					int delim = arg.indexOf("?");
					if(delim != -1)
					{
						arg = arg.substring(0, delim);
					}
					sendKeyedStatistics(channel, arg);

				} else
				{
					sendMessage(sender, line_header() + "sorry, but I didn't understand your command!");
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
					System.out.println("warning: data file didn't exist");
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
			File f = new File(data_file);
			if(!f.exists())
			{
				System.out.println("warning: data file didn't exist, ignoring");
				return;
			}

			// write new data file
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
		} catch(IOException ioe)
		{
			System.err.println("ioexception writing data: " + ioe);
			ioe.printStackTrace();
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
				message += key + " = " + values.get(key).intValue() + " || ";
			}
		}
		message += "(" + count + " matches found)";
		sendMessage(channel, message);
	}
}
