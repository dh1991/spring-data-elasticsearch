/*
 * Copyright 2018-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.elasticsearch;

import lombok.SneakyThrows;

import java.time.Duration;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.client.reactive.ReactiveElasticsearchClient;
import org.springframework.data.elasticsearch.client.reactive.ReactiveRestClients;
import org.springframework.data.elasticsearch.support.SearchHitsUtil;
import org.springframework.data.util.Version;
import org.springframework.util.ObjectUtils;

/**
 * @author Christoph Strobl
 * @author Mark Paluch
 * @currentRead Fool's Fate - Robin Hobb
 */
public final class TestUtils {

	private TestUtils() {}

	private static final ClientConfiguration CONFIG = ClientConfiguration.builder().connectedToLocalhost()
			.withConnectTimeout(Duration.ofSeconds(5)).withSocketTimeout(Duration.ofSeconds(3)).build();

	public static RestHighLevelClient restHighLevelClient() {
		return RestClients.create(CONFIG).rest();
	}

	public static ReactiveElasticsearchClient reactiveClient() {
		return ReactiveRestClients.create(CONFIG);
	}

	public static Version serverVersion() {

		try (RestHighLevelClient client = restHighLevelClient()) {

			org.elasticsearch.Version version = org.elasticsearch.Version
					.fromString(client.info(RequestOptions.DEFAULT).getVersion().getNumber());
			return new Version(version.major, version.minor, version.revision);

		} catch (Exception e) {
			return new Version(0, 0, 0);
		}
	}

	@SneakyThrows
	public static void deleteIndex(String... indexes) {

		if (ObjectUtils.isEmpty(indexes)) {
			return;
		}

		try (RestHighLevelClient client = restHighLevelClient()) {
			for (String index : indexes) {

				try {
					client.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
				} catch (ElasticsearchStatusException ex) {
					// just ignore it
				}
			}
		}
	}

	@SneakyThrows
	public static boolean isEmptyIndex(String indexName) {

		try (RestHighLevelClient client = restHighLevelClient()) {

			return 0L == SearchHitsUtil.getTotalCount(client
					.search(new SearchRequest(indexName)
							.source(SearchSourceBuilder.searchSource().query(QueryBuilders.matchAllQuery())), RequestOptions.DEFAULT)
					.getHits());
		}
	}

	public static OfType documentWithId(String id) {
		return new DocumentLookup(id);
	}

	public interface ExistsIn {
		boolean existsIn(String index);
	}

	public interface OfType extends ExistsIn {
		ExistsIn ofType(String type);
	}

	private static class DocumentLookup implements OfType {

		private String id;

		public DocumentLookup(String id) {
			this.id = id;
		}

		@Override
		@SneakyThrows
		public boolean existsIn(String index) {

			GetRequest request = new GetRequest(index).id(id);
			try (RestHighLevelClient client = restHighLevelClient()) {
				return client.get(request, RequestOptions.DEFAULT).isExists();
			}
		}

		@Override
		public ExistsIn ofType(String type) {
			return this;
		}
	}
}
