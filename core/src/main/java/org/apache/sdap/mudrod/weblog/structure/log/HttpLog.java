/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you 
 * may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sdap.mudrod.weblog.structure.log;

import com.google.gson.Gson;

import org.apache.sdap.mudrod.main.MudrodConstants;
import org.apache.sdap.mudrod.weblog.pre.CrawlerDetection;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents an Http log line. See
 * http://httpd.apache.org/docs/2.2/logs.html for more details.
 */

public class HttpLog extends WebLog implements Serializable {

	String Response;
	String Referer;
	String Browser;

	public HttpLog() {
		super();
	}

	@Override
	public double getBytes() {
		return this.Bytes;
	}

	public String getBrowser() {
		return this.Browser;
	}

	public String getResponse() {
		return this.Response;
	}

	public String getReferer() {
		return this.Referer;
	}

	public String getURL() {
		return "";
	}

	public static String parseFromLogLine(String log, Properties props, String type) throws ParseException {

		String logEntryPattern = "^([\\d.]+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+\\-]\\d{4})] \"(.+?)\" (\\d{3}) (\\d+|-) \"((?:[^\"]|\")+)\" \"([^\"]+)\"";
		final int numFields = 9;
		Pattern p = Pattern.compile(logEntryPattern);
		Matcher matcher;

		String lineJson = "{}";
		matcher = p.matcher(log);
		if (!matcher.matches() || numFields != matcher.groupCount()) {
			return lineJson;
		}

		String time = matcher.group(4);
		time = SwithtoNum(time);
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss");
		Date date = formatter.parse(time);

		String bytes = matcher.group(7);

		if ("-".equals(bytes)) {
			bytes = "0";
		}

		String request = matcher.group(5).toLowerCase();
		String agent = matcher.group(9);

		CrawlerDetection crawlerDe = new CrawlerDetection(props);
		if (crawlerDe.checkKnownCrawler(agent)) {
			return lineJson;
		} else {
			String[] mimeTypes = props.getProperty(MudrodConstants.BLACK_LIST_REQUEST).split(",");
			for (String mimeType : mimeTypes) {
				if (request.contains(mimeType)) {
					return lineJson;
				}
			}

			HttpLog httpLog = new HttpLog();

			httpLog.LogType = type;
			httpLog.IP = matcher.group(1);
			httpLog.Request = matcher.group(5);
			httpLog.Response = matcher.group(6);
			httpLog.Bytes = Double.parseDouble(bytes);
			
			httpLog.Referer = matcher.group(8);
			
			// standardize addresses
			if (httpLog.Referer.length() >= 31 && httpLog.Referer.substring(0, 4).equals("http")) {
			  if (httpLog.Referer.charAt(4) != 's') {
			    httpLog.Referer = httpLog.Referer.replaceFirst("http", "https");
			  }
			  if (httpLog.Referer.substring(8, 15).equals("podaac-")) {
			    // https://podaac-www.jpl.nasa.gov/dataaccess
			    // https://podaac-ftp.jpl.nasa.gov/dataaccess
			    if (httpLog.Referer.substring(15, 18).equals("www")) {
			      httpLog.Referer = httpLog.Referer.replaceFirst("-www", "");
			    } else if (httpLog.Referer.substring(15, 18).equals("ftp")) {
			      httpLog.Referer = httpLog.Referer.replaceFirst("-ftp", "");
			    } else if (httpLog.Referer.substring(15, 22).equals("opendap")) {
			      // https://podaac-opendap.jpl.nasa.gov/opendap/allData/aquarius/L3/mapped/V5/7day_running/SCI/2014/contents.html
			      httpLog.Referer = httpLog.Referer.replaceFirst("podaac-", "");
			    }
			  }
			}
			
			httpLog.Browser = matcher.group(9);
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");
			httpLog.Time = df.format(date);

			Gson gson = new Gson();
			lineJson = gson.toJson(httpLog);

			return lineJson;
		}
	}

	public static boolean checknull(WebLog s) {
		if (s == null) {
			return false;
		}
		return true;
	}

	public HttpLog createNewInstance(String logType) {
		if (logType.equals(MudrodConstants.ACCESS_LOG))
			return new ApacheAccessLog();
		if (logType.equals(MudrodConstants.THREDDS_LOG))
			return new ThreddsLog();
		if (logType.equals(MudrodConstants.OPENDAP_LOG))
			return new OpenDapLog();
		return new HttpLog();
	}
}
