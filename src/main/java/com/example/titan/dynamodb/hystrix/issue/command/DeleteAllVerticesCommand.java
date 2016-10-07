package com.example.titan.dynamodb.hystrix.issue.command;


import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.graphdb.tinkerpop.TitanBlueprintsGraph;

public class DeleteAllVerticesCommand extends HystrixCommand<Void>
{

	private TitanBlueprintsGraph titanGraph;
	private Integer commandNumber;

	public DeleteAllVerticesCommand(final Integer commandNumber, final TitanBlueprintsGraph titanGraph, final Integer timeout) {
		super(Setter
				.withGroupKey(HystrixCommandGroupKey.Factory.asKey("DeleteAllVerticesCommandGroup"))
				.andCommandKey(HystrixCommandKey.Factory.asKey("DeleteAllVerticesCommand"))
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
			tx.traversal().V().has("indexedProperty", "testVertex").toSet().forEach(element -> element.remove());

			System.out.println(commandNumber+" Before delete all commit: "+Thread.currentThread().getName());
			tx.commit();
			System.out.println(commandNumber+" After delete all commit: "+Thread.currentThread().getName());

		}
		catch (Exception e) {
			System.out.println(commandNumber+" Delete All Exception: interrupted!");
			interrupted = true;
			e.printStackTrace();
			throw e;
		}
		finally {
			if (interrupted && tx.isOpen()) {
				System.out.println(commandNumber+" Before delete all rollback: "+Thread.currentThread().getName());
				tx.rollback();
				System.out.println(commandNumber+" After delete all rollback: "+Thread.currentThread().getName());
			}
		}

		return null;
	}

}