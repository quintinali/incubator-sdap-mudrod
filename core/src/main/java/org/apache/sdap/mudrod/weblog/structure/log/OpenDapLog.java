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

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.sdap.mudrod.main.MudrodConstants;
import org.apache.sdap.mudrod.weblog.pre.CrawlerDetection;

import com.google.gson.Gson;

/**
 * This class represents an Opendap log line. See
 * http://httpd.apache.org/docs/2.2/logs.html for more details.
 */

public class OpenDapLog extends HttpLog implements Serializable {

	public OpenDapLog() {
		super();
	}
	
	public static String parseFromLogLine(String log, Properties props) throws ParseException {
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

		// *** need to be modified
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

			OpenDapLog OpendapLog = new OpenDapLog();

			// *** need to be modified
			OpendapLog.LogType = MudrodConstants.OPENDAP_LOG;
			OpendapLog.IP = matcher.group(1);
			OpendapLog.Request = matcher.group(5);
			OpendapLog.Response = matcher.group(6);
			OpendapLog.Bytes = Double.parseDouble(bytes);
			OpendapLog.Referer = matcher.group(8);
			OpendapLog.Browser = matcher.group(9);
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");
			OpendapLog.Time = df.format(date);

			Gson gson = new Gson();
			lineJson = gson.toJson(OpendapLog);

			return lineJson;

		}
	}
}
