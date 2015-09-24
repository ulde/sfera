package cc.sferalabs.sfera.core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.sferalabs.sfera.core.SystemNode;

/**
 * Interfaces for services to be started only when requested.
 * 
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public abstract class LazyService implements Service {

	private static final Logger logger = LoggerFactory.getLogger(LazyService.class);

	/**
	 * Constructs the service and registers it to Sfera life cycle
	 */
	protected LazyService() {
		SystemNode.addToLifeCycle(this);
		logger.debug("Service {} instatiated", getClass().getSimpleName());
	}

}
