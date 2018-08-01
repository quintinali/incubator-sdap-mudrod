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
import org.apache.sdap.mudrod.weblog.pre.ImportLogFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * This class represents an FTP access log line.
 */
public class FtpLog extends WebLog implements Serializable {

  private static final Logger LOG = LoggerFactory.getLogger(ImportLogFile.class);

  public FtpLog() {
    super();
  }

  public static FtpLog parseFromLogLine(String log, Properties props) {

    try {
      String ip = log.split(" +")[6];

      String time = log.split(" +")[1] + ":" + log.split(" +")[2] + ":" + log.split(" +")[3] + ":" + log.split(" +")[4];

      time = SwithtoNum(time);
      //System.out.println(time);
      SimpleDateFormat formatter = new SimpleDateFormat("MM:dd:HH:mm:ss:yyyy");
      Date date = formatter.parse(time);

      String bytes = log.split(" +")[7];

      String request = log.split(" +")[8].toLowerCase();

      if (!request.contains("/misc/") && !request.contains("readme")) {
        FtpLog ftplog = new FtpLog();
        ftplog.logType = MudrodConstants.FTP_LOG;
        ftplog.IP = ip;
        ftplog.request = request;
        ftplog.bytes = Double.parseDouble(bytes);

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");
        ftplog.time = df.format(date);

        return ftplog;
      }
    } catch (Exception e) {
      LOG.warn("Error parsing ftp log line [{}]. Skipping this line.", log, e);
    }
    return null;
  }

  public static String parseFromLogLineToJson(String log, Properties props) {

    FtpLog ftplog = FtpLog.parseFromLogLine(log, props);
    if (ftplog == null) {
      return "{}";
    }

    return new Gson().toJson(ftplog);
  }
}
