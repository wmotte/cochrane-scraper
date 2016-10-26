package pubmed.author;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import pubmed.author.sql.SQLConnection;
import pubmed.author.tools.Getter;
import pubmed.author.tools.Poster;
import pubmed.author.tools.URLEncoder;

public class Authors extends Thread {
	private String pubmedid;
	public boolean isDone = false;
	private String first;
	private String last;
	private String gender1;
	private String gender2;
	private String probability1 = "0.00";
	private String probability2 = "0.00";
	private SQLConnection sql;

	public Authors(String pubmedid, SQLConnection con) {
		this.pubmedid = pubmedid;
		this.sql = con;
	}

	public void run() {
		try {
			if (getNames()) {
				if (getFirstName("first")) {
					if (authorInDatabase("first") || getGender("first")) {
						// got gender.
					} else {
						System.err.println("FAIL:" + pubmedid + ": Retreiving gender for '" + first + "' failed.");
					}
				} else {
					System.err.println("FAIL:" + pubmedid + ": Retreiving firstname for '" + first + "' failed.");
				}
				if (getFirstName("last")) {
					if (authorInDatabase("last") || getGender("last")) {
						// got gender.
					} else {
						System.err.println("FAIL:" + pubmedid + ": Retreiving gender for '" + last + "' failed.");
					}
				} else {
					System.err.println("FAIL:" + pubmedid + ": Retreiving firstname for '" + last + "' failed.");
				}
			}

			if (!sql.insertAuthors(this.pubmedid, this.first, this.last, this.gender1, this.gender2, this.probability1,
					this.probability2)) {
				System.err.println("FAIL:" + pubmedid + ": Insert into database failed for: " + first + ", " + last);
			} else {
				System.out.println(this.toString());
			}
			/*
			 * if (!getNames()) throw new Exception(
			 * "Failed to get AUTHOR NAMES from https://www.ncbi.nlm.nih.gov/pubmed/?term="
			 * + pubmedid + "[uid]"); if (!getFirstName(this.first)) throw new
			 * Exception("FAILED: No first name for "+ first); if
			 * (!getFirstName(this.last)) throw new Exception(
			 * "FAILED: No first name for "+ last); if
			 * (authorInDatabase("first") & authorInDatabase("last")) { //maybe
			 * say that they're in database... } else { if (!getGender("first"))
			 * throw new Exception(
			 * "Failed to get GENDERS from https://api.genderize.io/?name="); }
			 */

			this.isDone = true;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			this.isDone = true;
		}
	}

	private boolean isGroup(String input) {
		String[] matches = new String[] { "Network", "Group", "Team", "network", "group", "team" };
		for (String s : matches)
			if (input.contains(s))
				return true;
		return false;
	}

	private boolean authorInDatabase(String which) {
		String output;
		if (which.equals("first"))
			output = sql.checkForAuthor(this.first);
		else
			output = sql.checkForAuthor(this.last);

		if (output != null && !output.isEmpty() && !output.equals("null#1.00")) {
			String[] out = output.split("#");
			if (which.equals("first")) {
				this.gender1 = out[0];
				this.probability1 = out[1];
			} else {
				this.gender2 = out[0];
				this.probability2 = out[1];
			}
			return true;
		}

		return false;
	}

	private boolean getNames() {
		String url = "https://www.ncbi.nlm.nih.gov/pubmed/?term=" + pubmedid + "[uid]";
		Document doc = null;
		try {
			doc = Jsoup.parse(Getter.getHTML(url));
			Elements authors = doc.getElementsByClass("auths");
			this.first = authors.first().getElementsByTag("a").first().html();
			this.last = authors.first().getElementsByTag("a").last().html();
			if (isGroup(this.last)) {
				Elements a = authors.first().getElementsByTag("a");
				this.last = a.get(a.size() - 2).html();
			}
			return true;
		} catch (Exception e) {
			System.err.println(e.toString() + " URL: " + url);
		}
		return false;
	}

	private boolean getFirstName(String who) throws InterruptedException {
		int attempts = 0;
		while (attempts <= 3) {
			try {
				Document doc;
				if (who.equals("first"))
					doc = Jsoup.parse(getForFirstName(this.first));
				else
					doc = Jsoup.parse(getForFirstName(this.last));
				
				
				Elements authors = doc.getElementsByClass("general");
				Element tablerow = authors.get(3).getElementsByTag("tr").get(1);
				String result;
				if (tablerow.getElementsByTag("a").size() < 2) {
					result = tablerow.getElementsByTag("td").get(2).html();
				} else {
					result = tablerow.getElementsByTag("a").first().html();
				}
				
				if (toFirstname(result).isEmpty()) {
					String oldresult = result;
					tablerow = authors.get(3).getElementsByTag("tr").get(2);
					if (tablerow.getElementsByTag("a").size() < 2) {
						result = tablerow.getElementsByTag("td").get(2).html();
					} else {
						result = tablerow.getElementsByTag("a").first().html();
					}
					if (!oldresult.split(",")[0].equals(result.split(",")[0])|toFirstname(result).isEmpty()) {
						result = oldresult;
					} else {
						
					}
				}

				if (who.equals("first"))
					this.first = result;
				else
					this.last = result;

				return true;
			} catch (Exception e) {
				attempts++;
				Thread.sleep(20);
			}
		}
		return false;
	}

	private boolean getGender(String who) {
		try {
			String firstname;
			boolean goodToGo;
			if (who.equals("first")) {
				goodToGo = (this.gender1 == null || this.gender1.isEmpty() || this.gender1.equals("null"));
				firstname = toFirstname(this.first);
				if (isGroup(this.first)) {
					this.gender1 = "genderless";
					return true;
				}
			} else {
				goodToGo = (this.gender2 == null || this.gender2.isEmpty() || this.gender2.equals("null"));
				firstname = toFirstname(this.last);
				if (isGroup(this.last)) {
					this.gender2 = "genderless";
					return true;
				}
			}

			if (goodToGo) {
				JSONObject result = new JSONObject(
						Getter.getHTML("https://api.genderize.io/?name=" + URLEncoder.encode(firstname)));
				if (!result.get("gender").equals(null)) {
					if (who.equals("first")) {
						this.gender1 = result.getString("gender");
						this.probability1 = result.getString("probability");
					} else {
						this.gender2 = result.getString("gender");
						this.probability2 = result.getString("probability");
					}
				} else {
					if (who.equals("first")) {
						this.gender1 = "notfound";
					} else {
						this.gender2 = "notfound";
					}
				}
			}
			return true;
		} catch (

		Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private String toFirstname(String name) {
		String[] nameparts = name.split(",")[1].split(" ");
		String firstname = "";
		for (String i : nameparts) {
			if (i.length() > 1 && i.matches(".*[AEIOUaeiou].*"))
				if (firstname.length() > 1)
					firstname += " " + i;
				else
					firstname += i;
		}
		return firstname;
	}

	@SuppressWarnings("unused")
	private boolean getGenders() {
		return (getGender("first") & getGender("last"));
	}

	private String getForFirstName(String name) {
		Poster post = new Poster("http://hgserver2.amc.nl/cgi-bin/miner/miner2.cgi");
		post.addParameter("query", URLEncoder.encode(name + "[AU]"));
		post.addParameter("abstractlimit", "25000");
		post.addParameter("col.author", "full");
		post.addParameter("subauthor", "full");
		post.addParameter("subword", URLEncoder.encode("(ti)"));
		post.addParameter("bool", "AND");
		post.addParameter("merge", "YES");
		post.addParameter("minimalcount", "2");
		post.addParameter("term", "");
		return post.execute();
	}

	public String toString() {
		return "================ " + this.pubmedid + " ================\n#FIRST:\nAuthor:\t\t" + this.first
				+ "\nGender:\t\t" + this.gender1 + "\nProbability:\t" + this.probability1 + "\n#LAST:\nAuthor:\t\t"
				+ this.last + "\nGender:\t\t" + this.gender2 + "\nProbability:\t" + this.probability2;
	}
}
