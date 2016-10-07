package com.example.titan.dynamodb.hystrix.issue.command;


import java.util.UUID;

import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.graphdb.tinkerpop.TitanBlueprintsGraph;

public class CreateVertexCommand extends HystrixCommand<Void>
{

	private TitanBlueprintsGraph titanGraph;
	private Integer commandNumber;

	public CreateVertexCommand(final Integer commandNumber, final TitanBlueprintsGraph titanGraph, final Integer timeout) {

		super(Setter
				.withGroupKey(HystrixCommandGroupKey.Factory.asKey("CreateVertexCommandGroup"))
				.andCommandKey(HystrixCommandKey.Factory.asKey("CreateVertexCommand"))
				.andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("CommandThreadPool"))
				.andCommandPropertiesDefaults(
						HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(timeout)));

		this.titanGraph = titanGraph;
		this.commandNumber = commandNumber;
	}

	@Override
	protected Void run() throws Exception {
		boolean interrupted = false;
		TitanTransaction tx = titanGraph.buildTransaction().start();

		try {

			for (int i = 0 ; i < 1 ; i++ ) {
				Vertex vertex = tx.addVertex("TestVertex");
				setVertexProperties(vertex);
			}
			System.out.println(commandNumber+" Before commit: "+Thread.currentThread().getName());
			tx.commit();
			System.out.println(commandNumber+" After commit: "+Thread.currentThread().getName());

		}
		catch (Exception e) {
			System.out.println(commandNumber+" Exception: "+e.getMessage()+" : "+Thread.currentThread().getName());
			interrupted = true;
			e.printStackTrace();
			throw e;
		}
		finally {
			if (interrupted && tx.isOpen()) {
				System.out.println(commandNumber+" Before rollback: "+Thread.currentThread().getName());
				tx.rollback();
				System.out.println(commandNumber+" After rollback: "+Thread.currentThread().getName());
			}
		}

		return null;
	}

	private void setVertexProperties(Vertex vertex) {

		vertex.property("indexedProperty", "testVertex");

		for(int i = 0; i < 10; i++ ) {
			String uuidKey = UUID.randomUUID().toString();
			vertex.property(uuidKey, "PropertyValue"+i);
		}
	}
}