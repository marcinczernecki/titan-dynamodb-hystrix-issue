package com.example.titan.dynamodb.hystrix.issue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ImportResource;

import com.example.titan.dynamodb.hystrix.issue.command.CreateVertexCommand;
import com.example.titan.dynamodb.hystrix.issue.command.DeleteAllVerticesCommand;
import com.google.common.collect.Lists;
import com.netflix.config.ConfigurationManager;
import com.thinkaurelius.titan.core.PropertyKey;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.core.schema.SchemaStatus;
import com.thinkaurelius.titan.core.schema.TitanGraphIndex;
import com.thinkaurelius.titan.core.schema.TitanManagement;
import com.thinkaurelius.titan.graphdb.tinkerpop.TitanBlueprintsGraph;

import rx.Observable;


@SpringBootApplication
@ImportResource("classpath:META-INF/applicationContext.xml")
public class TitanDynamodbHystrixIssueApplication {

	private static Integer count = 0;

	public static void main(String[] args) throws InterruptedException
	{

		Integer persistenceTimeout = 1000;
		Integer iterations = 100;

		ConfigurationManager.getConfigInstance().setProperty("hystrix.threadpool.default.coreSize", 10);
		ConfigurationManager.getConfigInstance().setProperty("hystrix.threadpool.default.maxQueueSize", 100);
		ConfigurationManager.getConfigInstance().setProperty("hystrix.threadpool.default.queueSizeRejectionThreshold", 100);

		ApplicationContext applicationContext = SpringApplication.run(TitanDynamodbHystrixIssueApplication.class, args);

		TitanBlueprintsGraph titanGraph = (TitanBlueprintsGraph)applicationContext.getBean("titanGraph");

		createIndexIfNeeded(titanGraph);

		for (int i = 0; i < iterations; i++) {

			final CreateVertexCommand createCommand = new CreateVertexCommand(i, titanGraph, persistenceTimeout);
			final Observable<Void> createCommandObservable = createCommand.toObservable();
			createCommandObservable.subscribe(ignore -> {
					System.out.println("Vertex successfully created: "+Thread.currentThread().getName());
					incrementCount();
				},
				throwable -> {
					System.out.println("exception: " + throwable.getMessage());
					incrementCount();
				}
			);

			TimeUnit.MILLISECONDS.sleep(persistenceTimeout);
//
//			final DeleteAllVerticesCommand deleteAllCommand = new DeleteAllVerticesCommand(i, titanGraph, persistenceTimeout);
//			final Observable<Void> deleteAllCommandObservable = deleteAllCommand.toObservable();
//			deleteAllCommandObservable.subscribe(ignore -> {
//						System.out.println("All vertices successfully deleted: "+Thread.currentThread().getName());
//						incrementCount();
//					},
//					throwable -> {
//						System.out.println("exception: " + throwable.getMessage());
//						incrementCount();
//					}
//			);
//			TimeUnit.MILLISECONDS.sleep(persistenceTimeout);

		}

		while (count < iterations) {
			TimeUnit.SECONDS.sleep(1);
		}

		if (!checkDataConsistency(titanGraph))
		{
			System.out.println("ERROR! Data consistency error!");
		}

	}

	public static synchronized void incrementCount() {
		count++;
	}

	private static boolean checkDataConsistency(TitanBlueprintsGraph titanGraph) {
		System.out.println("Verifying data consistency: "+Thread.currentThread().getName());

		boolean result = true;

		TitanTransaction tx = titanGraph.buildTransaction().start();
		List<Vertex> indexedVertexList = tx.traversal().V().has("indexedProperty", "testVertex").toList();

		for (Vertex vertex: indexedVertexList) {
			if (!verifyVertexProperties(vertex)) {
				System.out.println("ERROR! Vertex "+vertex.id()+" is not valid");
				result = false;
			};
		}

		final Long vertexCount = tx.traversal().V().count().next();

		if (indexedVertexList.size() != vertexCount) {
			System.out.println("ERROR! indexedVertexList.size() "+indexedVertexList.size()+" vertexCount: "+vertexCount);
			result = false;
		}

		tx.commit();
		System.out.println("Verifying data consistency done.");

		return result;
	}

	private static boolean verifyVertexProperties(Vertex vertex) {
		boolean result = true;
		try {

			result = result && vertex.keys().size() == 11;

		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}


	private static void createIndexIfNeeded(TitanBlueprintsGraph titanGraph){
		final TitanManagement mgmt = titanGraph.openManagement();
		try
		{
			System.out.println("Existing vertex labels:  "+ mgmt.getVertexLabels().toString());

			final List<TitanGraphIndex> existingIndexes = Lists.newArrayList(mgmt.getGraphIndexes(Vertex.class));

			boolean createIndex = true;
			for (final TitanGraphIndex index : existingIndexes)
			{
				final SchemaStatus indexStatus = index.getIndexStatus(index.getFieldKeys()[0]);
				System.out.println("Existing indexes: " + index.name()+" indexStatus: "+indexStatus);


				if (index.name().equals("by_indexedProperty") && indexStatus.equals(SchemaStatus.ENABLED)) {
					createIndex = false;
				}
			}

			if (createIndex) {
				PropertyKey indexedProperty = mgmt.getPropertyKey("indexedProperty");
				if (indexedProperty == null) {
					indexedProperty = mgmt.makePropertyKey("indexedProperty").dataType(String.class).make();
				}

				mgmt.buildIndex("by_indexedProperty", Vertex.class).addKey(indexedProperty).buildCompositeIndex();
				System.out.println("Creating index: by_indexedProperty");
			}

			mgmt.commit();
			System.out.println("TitanManagement committed");
		}
		catch (final Exception e)
		{
			e.printStackTrace();
			mgmt.rollback();
		}
	}


}
