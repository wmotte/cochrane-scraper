package cochrane.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import cochrane.scraper.sql.SQLConnection;
import cochrane.scraper.tools.Getter;

public class CochraneArticleIterator extends Thread {
	private String outputDir;
	private String topic;
	private final String TOPIC_BASE_URL = "http://www.cochranelibrary.com/topic/";
	private SQLConnection con;
	private final boolean SHOULD_BE_FREE = false;
	public boolean done = false;

	public CochraneArticleIterator(String outputFolder, String topic) {
		this.outputDir = outputFolder;
		this.topic = topic;
		this.con = new SQLConnection("localhost", "3306", "paul", "paul", "[paul3514]");
	}


	@Override
	public void run() {
		try {
			int pages = 1;
			con.open();
			for (int i = 1; i <= pages; i++) {
				
				//get all articles under current topic, scroll through the pages, and parse the references from each topic
				Document doc = Jsoup
						.parse(Getter.get(this.TOPIC_BASE_URL + this.topic + "/?per-page=100&page=" + i));
				Elements articles = doc.select("article[class='results-block__article']");
				
				for (Element article : articles) {
					String link = article.select("a[href]").select("a[class='results-block__link']").first()
							.attr("href");

					if (isFree(article)) {
						new CochraneReferencesParser(outputDir, link, this.con, this.topic).run();
						this.delay(100);
					}
				}
				
				//check if there are more than one pages.
				if (i == 1) {
					Elements block_count = doc.getElementsByClass("results-block__count-display");
					try {
						pages = (int) Math.ceil(Double.parseDouble(block_count.first().html().split(" ")[0]) / 100.0);
					} catch (NullPointerException e) {
						// ignore, pages = 1. (stays the same)
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("ITERATING THROUGH ARTICLES FAILED " + this.topic);
		} finally {
			con.close();
			done = true;
		}
	}

	//checks if we should parse the article if it's not accessible from this IP.
	@SuppressWarnings("unused")
	private boolean isFree(Element article) {
		if (SHOULD_BE_FREE && article
				.select("span[class='icon results-block__access-icon results-block__access-icon--free']").isEmpty())
			return false;
		return true;
	}

	//simple sleep method.
	public void delay(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
		}
	}
}
