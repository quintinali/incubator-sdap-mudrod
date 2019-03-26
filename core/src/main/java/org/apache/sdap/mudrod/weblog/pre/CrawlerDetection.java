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
package org.apache.sdap.mudrod.weblog.pre;

import org.apache.sdap.mudrod.discoveryengine.DiscoveryStepAbstract;
import org.apache.sdap.mudrod.driver.ESDriver;
import org.apache.sdap.mudrod.driver.SparkDriver;
import org.apache.sdap.mudrod.main.MudrodConstants;
import org.apache.sdap.mudrod.weblog.structure.log.HttpLog;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function2;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram.Order;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link DiscoveryStepAbstract} implementation which detects a known list of
 * Web crawlers which may may be present within, and pollute various logs acting
 * as input to Mudrod.
 */
public class CrawlerDetection extends LogAbstract {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(CrawlerDetection.class);

	/**
	 * Paramterized constructor to instantiate a configured instance of
	 * {@link CrawlerDetection}
	 *
	 * @param props
	 *            populated {@link java.util.Properties} object
	 * @param es
	 *            {@link ESDriver} object to use in crawler detection preprocessing.
	 * @param spark
	 *            {@link SparkDriver} object to use in crawler detection
	 *            preprocessing.
	 */
	public CrawlerDetection(Properties props, ESDriver es, SparkDriver spark) {
		super(props, es, spark);
	}

	public CrawlerDetection(Properties props) {
		super(props, null, null);
	}

	@Override
	public Object execute() {
		// http type
		LOG.info("Starting Crawler detection {}.", httpType);
		startTime = System.currentTimeMillis();
		try {
			checkByRateInParallel();
		} catch (InterruptedException | IOException e) {
			LOG.error("Encountered an error whilst detecting Web crawlers.", e);
		}
		endTime = System.currentTimeMillis();
		es.refreshIndex();
		LOG.info("Crawler detection complete. Time elapsed {} seconds", (endTime - startTime) / 1000);
		return null;
	}

	/**
	 * Check known crawler through crawler agent name list
	 *
	 * @param agent
	 *            name of a log line
	 * @return 1 if the log is initiated by crawler, 0 otherwise
	 */
	public boolean checkKnownCrawler(String agent) {
		String[] crawlers = props.getProperty(MudrodConstants.BLACK_LIST_AGENT).split(",");
		for (int i = 0; i < crawlers.length; i++) {
			if (agent.toLowerCase().contains(crawlers[i].trim()))
				return true;
		}
		return false;
	}

	void checkByRateInParallel() throws InterruptedException, IOException {

		// this.httpType
		JavaRDD<String> userRDD = getUserRDD(this.httpType);
		LOG.info("Original User count: {}", userRDD.count());

		int userCount = 0;
		userCount = userRDD.mapPartitions((FlatMapFunction<Iterator<String>, Integer>) iterator -> {
			ESDriver tmpEs = new ESDriver(props);
			tmpEs.createBulkProcessor();
			List<Integer> realUserNums = new ArrayList<>();
			while (iterator.hasNext()) {
				String s = iterator.next();
				Integer realUser = checkByRate(tmpEs, s);
				realUserNums.add(realUser);
			}
			tmpEs.destroyBulkProcessor();
			tmpEs.close();
			return realUserNums.iterator();
		}).reduce((Function2<Integer, Integer, Integer>) (a, b) -> a + b);

		LOG.info("Final user count: {}", Integer.toString(userCount));
	}

	private int checkByRate(ESDriver es, String user) {

		int rate = Integer.parseInt(props.getProperty(MudrodConstants.REQUEST_RATE));

		Pattern pattern = Pattern.compile("get (.*?) http/*");
		Matcher matcher;

		BoolQueryBuilder AccessFilterSearch = new BoolQueryBuilder();
		AccessFilterSearch.must(QueryBuilders.termQuery("IP", user));
		
		// cause error after adding the condition below.
		// It filtered out everything
//		AccessFilterSearch.must(QueryBuilders.termQuery("LogType", MudrodConstants.ACCESS_LOG));

		AggregationBuilder aggregation = AggregationBuilders.dateHistogram("by_minute").field("Time")
				.dateHistogramInterval(DateHistogramInterval.MINUTE).order(Order.COUNT_DESC);
		SearchResponse checkRobot = es.getClient().prepareSearch(logIndex).setTypes(httpType, ftpType)
				.setQuery(AccessFilterSearch).setSize(0).addAggregation(aggregation).execute().actionGet();

		Histogram agg = checkRobot.getAggregations().get("by_minute");

		List<? extends Histogram.Bucket> botList = agg.getBuckets();
		
		// how can I get the type of log type here?
		long maxCount = botList.get(0).getDocCount();
		if (maxCount >= rate) {
			return 0;
		} else {
			DateTime dt1 = null;
			int toLast = 0;
			
		  BoolQueryBuilder filterSearch = new BoolQueryBuilder();
		  filterSearch.must(QueryBuilders.termQuery("IP", user));
		  
			SearchResponse scrollResp = es.getClient().prepareSearch(logIndex).setTypes(httpType, ftpType)
					.setScroll(new TimeValue(60000)).setQuery(filterSearch).setSize(100).execute().actionGet();
			while (true) {
				for (SearchHit hit : scrollResp.getHits().getHits()) {
					Map<String, Object> result = hit.getSource();
					String logtype = (String) result.get("LogType");

					// ***

					if (logtype.equals(MudrodConstants.ACCESS_LOG) || logtype.equals(MudrodConstants.THREDDS_LOG)
							|| logtype.equals(MudrodConstants.OPENDAP_LOG)) {
						HttpLog creator = new HttpLog();
						String request = (String) result.get("Request");
						matcher = pattern.matcher(request.trim().toLowerCase());
						boolean find = false;
						while (matcher.find()) {
							request = matcher.group(1);
							result.put("RequestUrl",
									props.getProperty(creator.createNewInstance(logtype).getURL()) + request);
							find = true;
						}
						if (!find) {
							result.put("RequestUrl", request);
						}
					} else {
						result.put("RequestUrl", result.get("Request"));
					}

					DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
					DateTime dt2 = fmt.parseDateTime((String) result.get("Time"));

					if (dt1 == null) {
						toLast = 0;
					} else {
						toLast = Math.abs(Seconds.secondsBetween(dt1, dt2).getSeconds());
					}
					result.put("ToLast", toLast);
					IndexRequest ir = new IndexRequest(logIndex, cleanupType).source(result);

					es.getBulkProcessor().add(ir);
					dt1 = dt2;
				}

				scrollResp = es.getClient().prepareSearchScroll(scrollResp.getScrollId())
						.setScroll(new TimeValue(600000)).execute().actionGet();
				if (scrollResp.getHits().getHits().length == 0) {
					break;
				}
			}

		}

		return 1;
	}

	@Override
	public Object execute(Object o) {
		return null;
	}

}
